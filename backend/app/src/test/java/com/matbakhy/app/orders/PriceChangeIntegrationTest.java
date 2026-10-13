package com.matbakhy.app.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Clock;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Owner decision D13 (RQ-004) on PostgreSQL, TC-154..TC-156 server part: a stale quote is refused at placement with a
 * recoverable reason and leaves no order, stock or obligation; a concurrent price update and placement are serialized
 * (whichever commits first wins consistently); a committed order with a lost response is recovered at its original
 * price; later price changes never touch committed orders or their financial records. Price changes are made the way
 * the kitchen's edit does it (meal row locked FOR UPDATE, price and revision updated, a revision appended).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class PriceChangeIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired TransactionTemplate tx;

    TestCommerce fx;
    String subject;
    UUID customer;
    UUID kitchen;
    UUID zone;
    UUID mealA;
    UUID mealB;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        subject = UUID.randomUUID().toString();
        customer = logins.login(subject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, logins.login(UUID.randomUUID().toString(), "Cook").getPrincipal().id(), reviewer);
        zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        mealA = fx.meal(kitchen, 12000, "كشري", "Koshari");
        mealB = fx.meal(kitchen, 8000, "محشي", "Mahshi");
        slot = fx.slot(kitchen, 360, 20, mealA, 10, mealB, 10);
        address = fx.address(customer, zone);
    }

    @Test
    void a_raised_then_lowered_meal_price_refuses_the_stale_quote_without_side_effects_and_the_fresh_quote_places() throws Exception {
        UUID stale = quote(item(mealA, 2) + "," + item(mealB, 1), false);
        changePrice(mealA, 13000);
        MockHttpServletResponse refused = place(stale, UUID.randomUUID(), key());
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(body(refused).get("code").asString()).isEqualTo("PRICE_CHANGED");
        assertThat(refused.getContentAsString()).contains("MEAL_CHANGED");
        assertNothingCommitted();

        JsonNode fresh = quoteBody(item(mealA, 2) + "," + item(mealB, 1), false);
        assertThat(fresh.get("total").get("amountMinor").asLong()).isEqualTo(2 * 13000 + 8000);
        changePrice(mealA, 11000);
        assertThat(place(UUID.fromString(fresh.get("quoteId").asString()), UUID.randomUUID(), key()).getStatus()).isEqualTo(409);
        assertNothingCommitted();

        JsonNode latest = quoteBody(item(mealA, 2) + "," + item(mealB, 1), false);
        assertThat(latest.get("total").get("amountMinor").asLong()).isEqualTo(2 * 11000 + 8000);
        MockHttpServletResponse placed = place(UUID.fromString(latest.get("quoteId").asString()), UUID.randomUUID(), key());
        assertThat(placed.getStatus()).as(placed.getContentAsString()).isEqualTo(201);
        assertThat(body(placed).get("total").get("amountMinor").asLong()).isEqualTo(30000);
    }

    @Test
    void a_delivery_fee_change_alone_refuses_the_stale_quote_as_a_total_change() throws Exception {
        UUID stale = quote(item(mealA, 1), true);
        jdbc.update("UPDATE service_zone SET delivery_fee_minor = 3125, version = version + 1 WHERE id = ?", zone);
        MockHttpServletResponse refused = place(stale, UUID.randomUUID(), key());
        assertThat(refused.getStatus()).isEqualTo(409);
        assertThat(refused.getContentAsString()).contains("PRICE_CHANGED").contains("TOTAL_CHANGED").doesNotContain("MEAL_CHANGED");
        assertNothingCommitted();
        assertThat(quoteBody(item(mealA, 1), true).get("deliveryFee").get("amountMinor").asLong()).isEqualTo(3125);
    }

    /** TC-155: the price update holds its lock first; the placement waits for it and then sees the new price. */
    @Test
    void when_the_price_update_takes_effect_first_the_waiting_placement_is_refused() throws Exception {
        UUID stale = quote(item(mealA, 2), false);
        CountDownLatch updated = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CompletableFuture<Void> update = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            changePriceInCurrentTransaction(mealA, 15000);
            updated.countDown();
            await(release);
        }));
        assertThat(updated.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<MockHttpServletResponse> placement = CompletableFuture.supplyAsync(() -> placeQuietly(stale));
        Thread.sleep(1500);
        assertThat(placement).as("placement waits for the uncommitted price update").isNotDone();
        release.countDown();
        update.get(10, TimeUnit.SECONDS);
        MockHttpServletResponse r = placement.get(20, TimeUnit.SECONDS);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(409);
        assertThat(r.getContentAsString()).contains("PRICE_CHANGED");
        assertNothingCommitted();
    }

    /** TC-155: the placement validates and holds its locks first; the price update waits and applies afterwards. */
    @Test
    void when_the_placement_commits_first_the_order_keeps_its_validated_price_and_the_update_follows() throws Exception {
        UUID quoteId = quote(item(mealA, 2), false);
        CountDownLatch slotLocked = new CountDownLatch(1);
        CountDownLatch releaseSlot = new CountDownLatch(1);
        // Hold the meal-slot row so the placement stops after revalidating (stock is reserved after the price check).
        CompletableFuture<Void> holder = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> {
            jdbc.queryForList("SELECT id FROM meal_slot WHERE cook_slot_id = ? FOR UPDATE", slot);
            slotLocked.countDown();
            await(releaseSlot);
        }));
        assertThat(slotLocked.await(10, TimeUnit.SECONDS)).isTrue();
        CompletableFuture<MockHttpServletResponse> placement = CompletableFuture.supplyAsync(() -> placeQuietly(quoteId));
        Thread.sleep(1500);
        CompletableFuture<Void> update = CompletableFuture.runAsync(() -> tx.executeWithoutResult(s -> changePriceInCurrentTransaction(mealA, 15000)));
        Thread.sleep(1500);
        assertThat(update).as("the price update waits for the placement that validated first").isNotDone();
        releaseSlot.countDown();
        holder.get(10, TimeUnit.SECONDS);
        MockHttpServletResponse r = placement.get(20, TimeUnit.SECONDS);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        update.get(20, TimeUnit.SECONDS);
        UUID order = UUID.fromString(body(r).get("id").asString());
        assertThat(jdbc.queryForObject("SELECT unit_price_minor FROM order_item WHERE order_id = ?", Long.class, order)).isEqualTo(12000);
        assertThat(jdbc.queryForObject("SELECT total_minor FROM customer_order WHERE id = ?", Long.class, order)).isEqualTo(24000);
        assertThat(jdbc.queryForObject("SELECT price_minor FROM meal WHERE id = ?", Long.class, mealA)).isEqualTo(15000);
    }

    /** TC-156: committed, response lost, price changed: recovery returns the same order at its original price; no repricing. */
    @Test
    void a_committed_order_with_a_lost_response_is_recovered_at_its_original_price_after_a_price_change() throws Exception {
        UUID quoteId = quote(item(mealA, 2) + "," + item(mealB, 1), true);
        UUID request = UUID.randomUUID();
        MockHttpServletResponse first = place(quoteId, request, key());
        assertThat(first.getStatus()).isEqualTo(201);
        UUID order = UUID.fromString(body(first).get("id").asString());
        long total = 2 * 12000 + 8000 + 3000;
        long commission = jdbc.queryForObject("SELECT commission_minor FROM customer_order WHERE id = ?", Long.class, order);

        changePrice(mealA, 20000);
        jdbc.update("UPDATE service_zone SET delivery_fee_minor = 5000, version = version + 1 WHERE id = ?", zone);

        MockHttpServletResponse recovered = call(get("/api/v1/orders/by-request/" + request));
        assertThat(recovered.getStatus()).isEqualTo(200);
        assertThat(body(recovered).get("id").asString()).isEqualTo(order.toString());
        assertThat(body(recovered).get("total").get("amountMinor").asLong()).isEqualTo(total);
        // A retry with a new idempotency key but the same request reference returns the same order, never a repriced one.
        MockHttpServletResponse retried = place(quoteId, request, key());
        assertThat(body(retried).get("id").asString()).isEqualTo(order.toString());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE customer_id = ?", Integer.class, customer)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT total_minor FROM customer_order WHERE id = ?", Long.class, order)).isEqualTo(total);
        assertThat(jdbc.queryForObject("SELECT commission_minor FROM customer_order WHERE id = ?", Long.class, order)).isEqualTo(commission);
        assertThat(jdbc.queryForObject("SELECT unit_price_minor FROM order_item WHERE order_id = ? AND meal_id = ?", Long.class, order, mealA))
                .isEqualTo(12000);
        assertThat(jdbc.queryForObject("SELECT amount_minor FROM cash_obligation WHERE order_id = ?", Long.class, order)).isEqualTo(total);
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(reserved), 0) FROM meal_slot WHERE cook_slot_id = ?", Integer.class, slot)).isEqualTo(3);
    }

    // ---- helpers ----

    private void assertNothingCommitted() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE customer_id = ?", Integer.class, customer)).isZero();
        assertThat(jdbc.queryForObject("SELECT coalesce(sum(reserved), 0) FROM meal_slot WHERE cook_slot_id = ?", Integer.class, slot)).isZero();
        assertThat(jdbc.queryForObject("SELECT reserved FROM cook_slot WHERE id = ?", Integer.class, slot)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cash_obligation c JOIN customer_order o ON o.id = c.order_id WHERE o.customer_id = ?",
                Integer.class, customer)).isZero();
    }

    /** The kitchen edit's database effect (MealService.update): row lock, new price and revision, appended revision. */
    private void changePrice(UUID meal, long price) {
        tx.executeWithoutResult(s -> changePriceInCurrentTransaction(meal, price));
    }

    private void changePriceInCurrentTransaction(UUID meal, long price) {
        Integer revision = jdbc.queryForObject("SELECT revision FROM meal WHERE id = ? FOR UPDATE", Integer.class, meal);
        jdbc.update("UPDATE meal SET price_minor = ?, revision = ?, version = version + 1, updated_at = now() WHERE id = ?", price, revision + 1, meal);
        jdbc.update("""
                INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor)
                SELECT meal_id, ?, jsonb_set(snapshot, '{priceMinor}', to_jsonb(?::bigint)), ? FROM meal_revision WHERE meal_id = ? AND revision = ?""",
                revision + 1, price, price, meal, revision);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private MockHttpServletResponse placeQuietly(UUID quoteId) {
        try {
            return place(quoteId, UUID.randomUUID(), key());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private UUID quote(String items, boolean delivery) throws Exception {
        return UUID.fromString(quoteBody(items, delivery).get("quoteId").asString());
    }

    private JsonNode quoteBody(String items, boolean delivery) throws Exception {
        String req = "{\"items\":[" + items + "],\"slotId\":\"" + slot + "\",\"fulfillmentMode\":\"" + (delivery ? "DELIVERY" : "PICKUP") + "\""
                + (delivery ? ",\"addressId\":\"" + address + "\"" : "") + "}";
        MockHttpServletResponse r = call(post("/api/v1/quotes").content(req));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return body(r);
    }

    private MockHttpServletResponse place(UUID quoteId, UUID clientRequestId, String key) throws Exception {
        return call(post("/api/v1/orders").header("Idempotency-Key", key).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quoteId, clientRequestId, TestCommerce.TERMS, TestCommerce.CANCELLATION)));
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r) throws Exception {
        return mvc.perform(r.with(authentication(logins.login(subject, "Customer"))).with(csrf()).contentType(MediaType.APPLICATION_JSON))
                .andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private static String item(UUID meal, int qty) {
        return "{\"mealId\":\"" + meal + "\",\"quantity\":" + qty + "}";
    }

    private static String key() {
        return "price-" + UUID.randomUUID();
    }
}
