package com.matbakhy.app.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.MutableClock;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TC-022..TC-025, TC-118 on PostgreSQL: atomic placement (quote revalidation, deterministic stock locks, snapshots,
 * event, outbox and cash obligation in one transaction), injected failure leaving no partial state, idempotent and
 * permanent duplicate prevention, last-portion race across 20 customers, recovery lookup and owner-only views.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({TestLogins.class, OrderPlacementIntegrationTest.Beans.class})
class OrderPlacementIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;

    TestCommerce fx;
    String customerSubject;
    UUID customer;
    UUID kitchen;
    UUID zone;
    UUID mealA;
    UUID mealB;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).reset();
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        customer = customer(customerSubject);
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, logins.login(UUID.randomUUID().toString(), "Cook").getPrincipal().id(), reviewer);
        zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        mealA = fx.meal(kitchen, 12000, "كشري", "Koshari");
        mealB = fx.meal(kitchen, 8000, "محشي", null);
        slot = fx.slot(kitchen, 360, 10, mealA, 5, mealB, 5);
        address = fx.address(customer, zone);
    }

    @AfterEach
    void cleanUp() {
        ((MutableClock) clock).reset();
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_outbox ON outbox_event");
        jdbc.execute("DROP FUNCTION IF EXISTS test_fail_outbox()");
    }

    // ---- helpers ----

    private UUID customer(String subject) {
        UUID id = logins.login(subject, "Customer").getPrincipal().id();
        fx.verifyPhone(id);
        return id;
    }

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "Customer");
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, String subject) throws Exception {
        return mvc.perform(r.with(authentication(as(subject))).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private UUID quote(String subject, String items, boolean delivery, UUID addressId) throws Exception {
        String req = "{\"items\":[" + items + "],\"slotId\":\"" + slot + "\",\"fulfillmentMode\":\"" + (delivery ? "DELIVERY" : "PICKUP") + "\""
                + (delivery ? ",\"addressId\":\"" + addressId + "\"" : "") + "}";
        MockHttpServletResponse r = call(post("/api/v1/quotes").content(req), subject);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return UUID.fromString(body(r).get("quoteId").asString());
    }

    private String item(UUID meal, int qty) {
        return "{\"mealId\":\"" + meal + "\",\"quantity\":" + qty + "}";
    }

    private String placeBody(UUID quoteId, UUID clientRequestId) {
        return """
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"},"fulfillmentNote":"Ring twice"}"""
                .formatted(quoteId, clientRequestId, TestCommerce.TERMS, TestCommerce.CANCELLATION);
    }

    private MockHttpServletResponse place(String subject, String key, String bodyJson) throws Exception {
        MockHttpServletRequestBuilder r = post("/api/v1/orders").content(bodyJson);
        if (key != null) {
            r = r.header("Idempotency-Key", key);
        }
        return call(r, subject);
    }

    private static String key() {
        return "order-" + UUID.randomUUID();
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private int slotReserved() {
        return count("SELECT reserved FROM cook_slot WHERE id = ?", slot);
    }

    // ---- TC-118: atomic placement with snapshots ----

    @Test
    void placement_writes_order_snapshots_reservation_event_outbox_and_cash_obligation_together() throws Exception {
        UUID quoteId = quote(customerSubject, item(mealA, 2) + "," + item(mealB, 1), true, address);
        UUID requestId = UUID.randomUUID();
        MockHttpServletResponse r = place(customerSubject, key(), placeBody(quoteId, requestId));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        JsonNode order = body(r);
        UUID orderId = UUID.fromString(order.get("id").asString());
        assertThat(r.getHeader("Location")).isEqualTo("/api/v1/orders/" + orderId);
        assertThat(order.get("state").asString()).isEqualTo("PENDING_ACCEPTANCE");
        assertThat(order.get("publicNumber").asString()).startsWith("MB-");
        assertThat(order.get("subtotal").get("amountMinor").asLong()).isEqualTo(32000);
        assertThat(order.get("deliveryFee").get("amountMinor").asLong()).isEqualTo(3000);
        assertThat(order.get("total").get("amountMinor").asLong()).isEqualTo(35000);
        assertThat(order.get("items").size()).isEqualTo(2);
        assertThat(order.get("cashState").asString()).isEqualTo("DUE");
        assertThat(order.get("allowedActions").toString()).isEqualTo("[\"CANCEL\"]");
        assertThat(order.get("events").get(0).get("toState").asString()).isEqualTo("PENDING_ACCEPTANCE");
        assertThat(order.get("events").get(0).get("actorType").asString()).isEqualTo("CUSTOMER");
        assertThat(order.get("slot").get("id").asString()).isEqualTo(slot.toString());
        assertThat(order.get("handoverCode").isNull()).isTrue();
        String text = r.getContentAsString();
        assertThat(text).doesNotContain("Synthetic Kitchen").doesNotContain("Synthetic fixture location").doesNotContain("commission");

        assertThat(count("SELECT reserved FROM meal_slot WHERE cook_slot_id = ? AND meal_id = ?", slot, mealA)).isEqualTo(2);
        assertThat(count("SELECT reserved FROM meal_slot WHERE cook_slot_id = ? AND meal_id = ?", slot, mealB)).isEqualTo(1);
        assertThat(slotReserved()).isEqualTo(3);
        assertThat(count("SELECT count(*) FROM stock_allocation WHERE order_id = ? AND state = 'RESERVED'", orderId)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND event_type = 'ORDER_PLACED' AND status = 'PENDING'",
                orderId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT amount_minor FROM cash_obligation WHERE order_id = ? AND state = 'DUE'", Long.class, orderId))
                .isEqualTo(35000L);
        assertThat(jdbc.queryForObject("SELECT commission_minor FROM customer_order WHERE id = ?", Long.class, orderId))
                .as("10% synthetic commission on subtotal, excluding delivery").isEqualTo(3200L);
        assertThat(jdbc.queryForObject("SELECT address_snapshot->>'street' FROM customer_order WHERE id = ?", String.class, orderId))
                .isEqualTo("Synthetic street 7");
        assertThat(jdbc.queryForObject("SELECT contact_snapshot->>'phoneE164' FROM customer_order WHERE id = ?", String.class, orderId))
                .isEqualTo("+201001112233");
        assertThat(jdbc.queryForObject("SELECT outbox.payload::text FROM outbox_event outbox WHERE aggregate_id = ?", String.class, orderId))
                .as("no personal data in the outbox").doesNotContain("+20").doesNotContain("Synthetic street");
    }

    @Test
    void order_history_keeps_the_snapshot_after_the_meal_changes() throws Exception {
        UUID quoteId = quote(customerSubject, item(mealA, 1), false, null);
        UUID orderId = UUID.fromString(body(place(customerSubject, key(), placeBody(quoteId, UUID.randomUUID()))).get("id").asString());
        jdbc.update("INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor) VALUES (?, 2, ?::jsonb, 99000)", mealA,
                "{\"titleAr\":\"اسم جديد\",\"allergenDeclaration\":\"NONE_DECLARED\",\"allergenCodes\":[],\"priceMinor\":99000}");
        jdbc.update("UPDATE meal SET title_ar = 'اسم جديد', price_minor = 99000, revision = 2 WHERE id = ?", mealA);
        jdbc.update("UPDATE meal SET status = 'ARCHIVED', archived_at = now() WHERE id = ?", mealA);

        JsonNode view = body(call(get("/api/v1/orders/" + orderId), customerSubject));
        assertThat(view.get("items").get(0).get("title").asString()).isEqualTo("كشري");
        assertThat(view.get("items").get(0).get("unitPrice").get("amountMinor").asLong()).isEqualTo(12000);
        assertThat(view.get("items").get(0).get("allergenStatement").asString()).isEqualTo("GLUTEN");
        assertThat(body(mvc.perform(get("/api/v1/orders/" + orderId).with(authentication(as(customerSubject))).header("Accept-Language", "en"))
                .andReturn().getResponse()).get("items").get(0).get("title").asString()).isEqualTo("Koshari");
        assertThatThrownBy(() -> jdbc.update("UPDATE order_item SET unit_price_minor = 1 WHERE order_id = ?", orderId))
                .hasMessageContaining("append-only");
    }

    // ---- TC-022: rollback leaves no partial state; retry completes once ----

    @Test
    void an_injected_failure_after_the_stock_update_rolls_everything_back_and_a_retry_completes_once() throws Exception {
        UUID quoteId = quote(customerSubject, item(mealA, 2), true, address);
        jdbc.execute("""
                CREATE FUNCTION test_fail_outbox() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.payload->>'kitchenId' = '%s' THEN RAISE EXCEPTION 'injected failure before commit'; END IF;
                    RETURN NEW;
                END $$""".formatted(kitchen));
        jdbc.execute("CREATE TRIGGER test_fail_outbox BEFORE INSERT ON outbox_event FOR EACH ROW EXECUTE FUNCTION test_fail_outbox()");
        String k = key();
        UUID requestId = UUID.randomUUID();
        MockHttpServletResponse failed = place(customerSubject, k, placeBody(quoteId, requestId));
        assertThat(failed.getStatus()).isEqualTo(500);
        assertThat(failed.getContentAsString()).contains("INTERNAL_ERROR").doesNotContain("injected");

        assertThat(count("SELECT count(*) FROM customer_order WHERE customer_id = ?", customer)).isZero();
        assertThat(count("SELECT count(*) FROM stock_allocation a JOIN meal_slot ms ON ms.id = a.meal_slot_id WHERE ms.cook_slot_id = ?", slot)).isZero();
        assertThat(slotReserved()).isZero();
        assertThat(count("SELECT reserved FROM meal_slot WHERE cook_slot_id = ? AND meal_id = ?", slot, mealA)).isZero();
        assertThat(count("SELECT count(*) FROM idempotency_record WHERE actor_id = ?", customer)).isZero();
        assertThat(count("SELECT count(*) FROM cash_obligation c JOIN customer_order o ON o.id = c.order_id WHERE o.customer_id = ?", customer))
                .isZero();

        jdbc.execute("DROP TRIGGER test_fail_outbox ON outbox_event");
        MockHttpServletResponse retried = place(customerSubject, k, placeBody(quoteId, requestId));
        assertThat(retried.getStatus()).as(retried.getContentAsString()).isEqualTo(201);
        assertThat(count("SELECT count(*) FROM customer_order WHERE customer_id = ?", customer)).isEqualTo(1);
        assertThat(slotReserved()).isEqualTo(2);
    }

    // ---- TC-023 / TC-024: duplicates and key reuse ----

    @Test
    void retries_after_timeout_and_after_idempotency_retention_create_exactly_one_order() throws Exception {
        UUID quoteId = quote(customerSubject, item(mealA, 1), false, null);
        UUID requestId = UUID.randomUUID();
        String k = key();
        JsonNode first = body(place(customerSubject, k, placeBody(quoteId, requestId)));
        MockHttpServletResponse replay = place(customerSubject, k, placeBody(quoteId, requestId));
        assertThat(replay.getStatus()).isEqualTo(201);
        assertThat(body(replay).get("id")).isEqualTo(first.get("id"));

        // Cached idempotency response gone (retention elapsed): the permanent clientRequestId still prevents a duplicate.
        jdbc.update("DELETE FROM idempotency_record WHERE actor_id = ?", customer);
        assertThat(body(place(customerSubject, k, placeBody(quoteId, requestId))).get("id")).isEqualTo(first.get("id"));
        assertThat(body(place(customerSubject, key(), placeBody(quoteId, requestId))).get("id")).as("new key, same request").isEqualTo(first.get("id"));

        assertThat(count("SELECT count(*) FROM customer_order WHERE customer_id = ?", customer)).isEqualTo(1);
        assertThat(slotReserved()).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM order_event e JOIN customer_order o ON o.id = e.order_id WHERE o.customer_id = ?", customer))
                .isEqualTo(1);

        JsonNode recovered = body(call(get("/api/v1/orders/by-request/" + requestId), customerSubject));
        assertThat(recovered.get("id")).isEqualTo(first.get("id"));
        assertThat(call(get("/api/v1/orders/by-request/" + UUID.randomUUID()), customerSubject).getStatus()).isEqualTo(404);
    }

    /** QA-002: after the temporary idempotency record is gone, the permanent reference still rejects a materially different request. */
    @Test
    void a_reused_request_reference_with_a_changed_field_conflicts_even_after_the_cache_is_gone() throws Exception {
        UUID quoteId = quote(customerSubject, item(mealA, 1), false, null);
        UUID requestId = UUID.randomUUID();
        String k = key();
        JsonNode first = body(place(customerSubject, k, placeBody(quoteId, requestId)));
        jdbc.update("DELETE FROM idempotency_record WHERE actor_id = ?", customer);

        String changedNote = placeBody(quoteId, requestId).replace("Ring twice", "Leave at the door");
        MockHttpServletResponse changed = place(customerSubject, k, changedNote);
        assertThat(changed.getStatus()).isEqualTo(409);
        assertThat(changed.getContentAsString()).contains("IDEMPOTENCY_CONFLICT");
        assertThat(place(customerSubject, key(), changedNote).getStatus()).as("new key, same reference, changed note").isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT fulfillment_note FROM customer_order WHERE id = ?", String.class, UUID.fromString(first.get("id").asString())))
                .isEqualTo("Ring twice");
        assertThat(count("SELECT count(*) FROM customer_order WHERE customer_id = ?", customer)).isEqualTo(1);

        // A safe retry of the identical request, and a whitespace-only difference in the note, still recover the same order.
        assertThat(body(place(customerSubject, key(), placeBody(quoteId, requestId))).get("id")).isEqualTo(first.get("id"));
        assertThat(body(place(customerSubject, key(), placeBody(quoteId, requestId).replace("Ring twice", "  Ring twice  "))).get("id"))
                .isEqualTo(first.get("id"));
    }

    @Test
    void orders_placed_before_the_fingerprint_migration_keep_quote_only_recovery() throws Exception {
        UUID quoteId = quote(customerSubject, item(mealA, 1), false, null);
        UUID requestId = UUID.randomUUID();
        JsonNode first = body(place(customerSubject, key(), placeBody(quoteId, requestId)));
        jdbc.update("DELETE FROM idempotency_record WHERE actor_id = ?", customer);
        jdbc.update("UPDATE customer_order SET request_fingerprint = NULL WHERE customer_id = ? AND client_request_id = ?", customer, requestId);
        assertThat(body(place(customerSubject, key(), placeBody(quoteId, requestId).replace("Ring twice", "Other"))).get("id"))
                .as("legacy order: no stored fingerprint, same quote recovers").isEqualTo(first.get("id"));
    }

    @Test
    void reusing_a_key_or_request_reference_with_another_body_conflicts_and_leaves_the_order_unchanged() throws Exception {
        UUID quoteA = quote(customerSubject, item(mealA, 1), false, null);
        UUID quoteB = quote(customerSubject, item(mealB, 1), false, null);
        UUID requestId = UUID.randomUUID();
        String k = key();
        JsonNode original = body(place(customerSubject, k, placeBody(quoteA, requestId)));

        MockHttpServletResponse sameKey = place(customerSubject, k, placeBody(quoteB, UUID.randomUUID()));
        assertThat(sameKey.getStatus()).isEqualTo(409);
        assertThat(sameKey.getContentAsString()).contains("IDEMPOTENCY_CONFLICT");
        MockHttpServletResponse sameRequest = place(customerSubject, key(), placeBody(quoteB, requestId));
        assertThat(sameRequest.getStatus()).isEqualTo(409);
        assertThat(sameRequest.getContentAsString()).contains("IDEMPOTENCY_CONFLICT");
        MockHttpServletResponse reusedQuote = place(customerSubject, key(), placeBody(quoteA, UUID.randomUUID()));
        assertThat(reusedQuote.getStatus()).isEqualTo(409);
        assertThat(reusedQuote.getContentAsString()).contains("QUOTE_ALREADY_USED");

        assertThat(count("SELECT count(*) FROM customer_order WHERE customer_id = ?", customer)).isEqualTo(1);
        JsonNode now = body(call(get("/api/v1/orders/" + original.get("id").asString()), customerSubject));
        assertThat(now.get("version")).isEqualTo(original.get("version"));
        assertThat(now.get("items").get(0).get("mealId").asString()).isEqualTo(mealA.toString());
        assertThat(slotReserved()).isEqualTo(1);
    }

    // ---- TC-025: last portion race ----

    @Test
    void twenty_customers_racing_for_the_last_portion_produce_exactly_one_order() throws Exception {
        UUID last = fx.meal(kitchen, 5000, "آخر طبق", null);
        UUID raceSlot = fx.slot(kitchen, 420, 10, last, 1);
        List<String> subjects = new ArrayList<>();
        List<UUID> quotesForRace = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String s = UUID.randomUUID().toString();
            customer(s);
            subjects.add(s);
            MockHttpServletResponse q = call(post("/api/v1/quotes").content("{\"items\":[" + item(last, 1) + "],\"slotId\":\"" + raceSlot
                    + "\",\"fulfillmentMode\":\"PICKUP\"}"), s);
            assertThat(q.getStatus()).as("quotes hold no stock").isEqualTo(201);
            quotesForRace.add(UUID.fromString(body(q).get("quoteId").asString()));
        }
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(20);
        List<Future<MockHttpServletResponse>> results = new ArrayList<>();
        try {
            for (int i = 0; i < 20; i++) {
                String s = subjects.get(i);
                UUID q = quotesForRace.get(i);
                Callable<MockHttpServletResponse> task = () -> {
                    start.await();
                    return place(s, key(), placeBody(q, UUID.randomUUID()));
                };
                results.add(pool.submit(task));
            }
            start.countDown();
            int created = 0;
            int stock = 0;
            for (Future<MockHttpServletResponse> f : results) {
                MockHttpServletResponse r = f.get();
                if (r.getStatus() == 201) {
                    created++;
                } else {
                    assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(409);
                    assertThat(r.getContentAsString()).containsAnyOf("STOCK_CHANGED");
                    stock++;
                }
            }
            assertThat(created).isEqualTo(1);
            assertThat(stock).isEqualTo(19);
        } finally {
            pool.shutdownNow();
        }
        assertThat(count("SELECT reserved FROM cook_slot WHERE id = ?", raceSlot)).isEqualTo(1);
        assertThat(count("SELECT reserved FROM meal_slot WHERE cook_slot_id = ?", raceSlot)).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM customer_order WHERE slot_id = ?", raceSlot)).isEqualTo(1);
        assertThat(count("SELECT coalesce(sum(a.quantity), 0) FROM stock_allocation a JOIN meal_slot ms ON ms.id = a.meal_slot_id "
                + "WHERE ms.cook_slot_id = ?", raceSlot)).isEqualTo(1);
    }

    // ---- placement guards ----

    @Test
    void stale_expired_foreign_or_unaccepted_quotes_and_forged_fields_are_refused() throws Exception {
        UUID quoteId = quote(customerSubject, item(mealA, 1), false, null);
        assertThat(place(customerSubject, null, placeBody(quoteId, UUID.randomUUID())).getStatus()).as("Idempotency-Key required").isEqualTo(400);
        String unaccepted = placeBody(quoteId, UUID.randomUUID()).replace(TestCommerce.CANCELLATION, "old-cancel-v0");
        MockHttpServletResponse notAccepted = place(customerSubject, key(), unaccepted);
        assertThat(notAccepted.getStatus()).isEqualTo(409);
        assertThat(notAccepted.getContentAsString()).contains("POLICY_NOT_ACCEPTED");
        for (String forged : new String[] {"\"state\":\"COMPLETED\"", "\"customerId\":\"" + UUID.randomUUID() + "\"",
                "\"total\":{\"amountMinor\":1,\"currency\":\"EGP\"}"}) {
            assertThat(place(customerSubject, key(), placeBody(quoteId, UUID.randomUUID()).replaceFirst("\\{", "{" + forged + ","))
                    .getStatus()).as(forged).isEqualTo(400);
        }
        String otherSubject = UUID.randomUUID().toString();
        customer(otherSubject);
        assertThat(place(otherSubject, key(), placeBody(quoteId, UUID.randomUUID())).getStatus()).as("another customer's quote").isEqualTo(404);

        jdbc.update("INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor) VALUES (?, 2, '{}'::jsonb, 13000)", mealA);
        jdbc.update("UPDATE meal SET price_minor = 13000, revision = 2 WHERE id = ?", mealA);
        MockHttpServletResponse stale = place(customerSubject, key(), placeBody(quoteId, UUID.randomUUID()));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("PRICE_CHANGED").contains("MEAL_CHANGED");

        UUID expiring = quote(customerSubject, item(mealB, 1), false, null);
        ((MutableClock) clock).advance(Duration.ofSeconds(301));
        assertThat(place(customerSubject, key(), placeBody(expiring, UUID.randomUUID())).getContentAsString()).contains("QUOTE_EXPIRED");
        assertThat(count("SELECT count(*) FROM customer_order WHERE customer_id = ?", customer)).isZero();
        assertThat(slotReserved()).isZero();
    }

    // ---- owner-only views ----

    @Test
    void customers_see_only_their_own_orders_with_paging_and_events() throws Exception {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            UUID q = quote(customerSubject, item(mealB, 1), false, null);
            ids.add(body(place(customerSubject, key(), placeBody(q, UUID.randomUUID()))).get("id").asString());
        }
        String otherSubject = UUID.randomUUID().toString();
        customer(otherSubject);
        for (String id : ids) {
            assertThat(call(get("/api/v1/orders/" + id), otherSubject).getStatus()).isEqualTo(404);
            assertThat(call(get("/api/v1/orders/" + id + "/events"), otherSubject).getStatus()).isEqualTo(404);
        }
        assertThat(body(call(get("/api/v1/orders"), otherSubject)).get("items").size()).isZero();

        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder r = get("/api/v1/orders").param("limit", "2").param("stateGroup", "ACTIVE");
            if (cursor != null) {
                r = r.param("cursor", cursor);
            }
            JsonNode page = body(call(r, customerSubject));
            page.get("items").forEach(o -> seen.add(o.get("id").asString()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);
        assertThat(seen).containsExactlyInAnyOrderElementsOf(ids).doesNotHaveDuplicates();
        assertThat(body(call(get("/api/v1/orders").param("stateGroup", "PAST"), customerSubject)).get("items").size()).isZero();
        JsonNode events = body(call(get("/api/v1/orders/" + ids.getFirst() + "/events"), customerSubject));
        assertThat(events.get("items").get(0).get("sequence").asInt()).isEqualTo(1);
        assertThat(call(get("/api/v1/orders").param("stateGroup", "ALL"), customerSubject).getStatus()).isEqualTo(422);
    }

    // ---- Retrospective regression coverage (2026-10-12 hardening pass; expectations from BR-006, BR-022, BR-025) ----

    private UUID quoteIn(String subject, UUID slotId, String items) throws Exception {
        MockHttpServletResponse r = call(post("/api/v1/quotes").content(
                "{\"items\":[" + items + "],\"slotId\":\"" + slotId + "\",\"fulfillmentMode\":\"PICKUP\"}"), subject);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return UUID.fromString(body(r).get("quoteId").asString());
    }

    @Test
    void a_two_meal_order_is_refused_without_partial_reservation_once_the_kitchen_slot_total_is_used_up() throws Exception {
        UUID tight = fx.slot(kitchen, 600, 4, mealA, 5, mealB, 5);
        UUID twoMeals = quoteIn(customerSubject, tight, item(mealA, 2) + "," + item(mealB, 2));
        String otherSubject = UUID.randomUUID().toString();
        customer(otherSubject);
        MockHttpServletResponse first = place(otherSubject, key(), placeBody(quoteIn(otherSubject, tight, item(mealA, 2)), UUID.randomUUID()));
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(201);

        MockHttpServletResponse late = place(customerSubject, key(), placeBody(twoMeals, UUID.randomUUID()));
        assertThat(late.getStatus()).as(late.getContentAsString()).isEqualTo(409);
        assertThat(late.getContentAsString()).contains("STOCK_CHANGED");
        assertThat(count("SELECT count(*) FROM customer_order WHERE customer_id = ?", customer)).isZero();
        assertThat(count("SELECT reserved FROM cook_slot WHERE id = ?", tight)).as("only the first order's portions").isEqualTo(2);
        assertThat(count("SELECT coalesce(sum(reserved), 0) FROM meal_slot WHERE cook_slot_id = ?", tight)).isEqualTo(2);
        assertThat(count("SELECT count(*) FROM stock_allocation a JOIN customer_order o ON o.id = a.order_id WHERE o.customer_id = ?", customer)).isZero();
    }

    @Test
    void another_customer_cannot_find_an_order_by_its_client_request_id() throws Exception {
        UUID requestId = UUID.randomUUID();
        MockHttpServletResponse placed = place(customerSubject, key(), placeBody(quote(customerSubject, item(mealA, 1), false, null), requestId));
        assertThat(placed.getStatus()).as(placed.getContentAsString()).isEqualTo(201);
        String otherSubject = UUID.randomUUID().toString();
        customer(otherSubject);
        MockHttpServletResponse foreign = call(get("/api/v1/orders/by-request/" + requestId), otherSubject);
        assertThat(foreign.getStatus()).isEqualTo(404);
        assertThat(foreign.getContentAsString()).doesNotContain(body(placed).get("id").asString());
        assertThat(call(get("/api/v1/orders/by-request/" + requestId), customerSubject).getStatus()).isEqualTo(200);
    }
}
