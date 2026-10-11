package com.matbakhy.app.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.MutableClock;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.orders.api.QuoteDirectory;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TC-018..TC-021, TC-117 on PostgreSQL: one kitchen and slot per quote, integer totals computed only by the
 * server (forged amounts rejected), serviceability without silent conversion, 5-minute expiry, no stock hold and
 * revalidation of stale quotes with recoverable reasons. Catalogue rows are synthetic fixtures.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({TestLogins.class, QuoteIntegrationTest.Beans.class})
class QuoteIntegrationTest {

    static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

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
    @Autowired QuoteDirectory quotes;
    @Autowired Clock clock;

    String customerSubject;
    UUID customer;
    UUID kitchen;
    UUID zone;
    UUID unservedZone;
    UUID mealA;
    UUID mealB;
    UUID slot;
    UUID address;
    int policy;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).reset();
        policy = activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        customer = verifiedCustomer(customerSubject);
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, logins.login(UUID.randomUUID().toString(), "Cook").getPrincipal().id(), reviewer);
        zone = zone(3000);
        unservedZone = zone(2000);
        jdbc.update("INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES (?, ?)", kitchen, zone);
        mealA = meal(kitchen, 12000);
        mealB = meal(kitchen, 12000);
        slot = slot(kitchen, 6, 10, mealA, 5, mealB, 5);
        address = address(customer, zone);
    }

    @AfterEach
    void resetClock() {
        ((MutableClock) clock).reset();
    }

    // ---- fixtures ----

    private int activatePolicy(int taxBps) {
        jdbc.update("UPDATE commerce_policy SET status = 'RETIRED' WHERE status = 'ACTIVE'");
        Integer next = jdbc.queryForObject("SELECT coalesce(max(version), 0) + 1 FROM commerce_policy", Integer.class);
        jdbc.update("""
                INSERT INTO commerce_policy (version, label, synthetic, status, tax_rate_bps, commission_rate_bps, max_item_quantity,
                                             quote_ttl_seconds, acceptance_window_minutes, min_acceptance_minutes, cancellation_policy_version)
                VALUES (?, 'SYNTHETIC test policy', true, 'ACTIVE', ?, 1000, 10, 300, 15, 5, 'synthetic-cancel-v1')""", next, taxBps);
        return next;
    }

    private UUID verifiedCustomer(String subject) {
        UUID id = logins.login(subject, "Customer").getPrincipal().id();
        jdbc.update("UPDATE app_user SET phone_e164 = '+201001112233', phone_verified_at = now() WHERE id = ?", id);
        return id;
    }

    private UUID zone(long fee) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                + "VALUES (?, ?, 'منطقة', 'Zone', 'مدينة', 'City', true, ?)", id, "z-" + id.toString().substring(0, 8), fee);
        return id;
    }

    private UUID meal(UUID kitchenId, long price) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO meal (id, kitchen_id, status, title_ar, title_en, description_ar, portion_ar, ingredients_ar, allergen_declaration,
                                  price_minor, published_at)
                VALUES (?, ?, 'PUBLISHED', 'وجبة تجريبية', 'Test meal', 'وصف تجريبي للوجبة', 'علبة', 'مكونات', 'NONE_DECLARED', ?, now())""",
                id, kitchenId, price);
        jdbc.update("INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor) VALUES (?, 1, '{}'::jsonb, ?)", id, price);
        return id;
    }

    private UUID slot(UUID kitchenId, int hoursAhead, int maxPortions, Object... mealCapacity) {
        return slotInMinutes(kitchenId, hoursAhead * 60, maxPortions, mealCapacity);
    }

    private UUID slotInMinutes(UUID kitchenId, int minutesAhead, int maxPortions, Object... mealCapacity) {
        ZonedDateTime start = ZonedDateTime.ofInstant(clock.instant().plus(Duration.ofMinutes(minutesAhead)), CAIRO).withSecond(0).withNano(0);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, state)
                VALUES (?, ?, ?, ?, ?, 120, ?, ?, 'OPEN')""",
                id, kitchenId, start.toLocalDate(), Timestamp.from(start.toInstant()), Timestamp.from(start.plusMinutes(30).toInstant()),
                Timestamp.from(start.minusMinutes(120).toInstant()), maxPortions);
        for (int i = 0; i < mealCapacity.length; i += 2) {
            jdbc.update("INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity) VALUES (?, ?, ?, ?)", UUID.randomUUID(), id,
                    mealCapacity[i], mealCapacity[i + 1]);
        }
        return id;
    }

    private UUID address(UUID owner, UUID zoneId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO address (id, user_id, label, recipient_name, phone_e164, governorate, city, zone_id, street, building)
                VALUES (?, ?, 'Home', 'Synthetic Recipient', '+201001112233', 'Cairo', 'Cairo', ?, 'Synthetic street', '1')""", id, owner, zoneId);
        return id;
    }

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "Customer");
    }

    private MockHttpServletResponse quote(String body) throws Exception {
        return quote(customerSubject, body);
    }

    private MockHttpServletResponse quote(String subject, String body) throws Exception {
        return mvc.perform(post("/api/v1/quotes").with(authentication(as(subject))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(body)).andReturn().getResponse();
    }

    private JsonNode created(MockHttpServletResponse r) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return json.readTree(r.getContentAsString());
    }

    private String delivery(UUID slotId, UUID addressId, String items) {
        return "{\"items\":[" + items + "],\"slotId\":\"" + slotId + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + addressId + "\"}";
    }

    private String pickup(UUID slotId, String items) {
        return "{\"items\":[" + items + "],\"slotId\":\"" + slotId + "\",\"fulfillmentMode\":\"PICKUP\"}";
    }

    private String item(UUID meal, int qty) {
        return "{\"mealId\":\"" + meal + "\",\"quantity\":" + qty + "}";
    }

    private int quoteCount() {
        return jdbc.queryForObject("SELECT count(*) FROM quote WHERE customer_id = ?", Integer.class, customer);
    }

    private ApiException rejection(UUID quoteId) {
        try {
            quotes.requireValid(customer, quoteId);
        } catch (ApiException e) {
            return e;
        }
        throw new AssertionError("quote unexpectedly valid");
    }

    // ---- TC-019: authoritative integer totals ----

    @Test
    void the_server_computes_exact_totals_and_forged_amounts_are_rejected() throws Exception {
        JsonNode q = created(quote(delivery(slot, address, item(mealA, 1) + "," + item(mealB, 1))));
        assertThat(q.get("subtotal").get("amountMinor").asLong()).isEqualTo(24000);
        assertThat(q.get("deliveryFee").get("amountMinor").asLong()).isEqualTo(3000);
        assertThat(q.get("tax").get("amountMinor").asLong()).isZero();
        assertThat(q.get("total").get("amountMinor").asLong()).isEqualTo(27000);
        assertThat(q.get("total").get("currency").asString()).isEqualTo("EGP");
        assertThat(q.get("items").get(0).get("lineTotal").get("amountMinor").asLong()).isEqualTo(12000);
        assertThat(q.get("items").get(0).get("mealRevision").asInt()).isEqualTo(1);
        assertThat(q.get("warnings").toString()).contains("SYNTHETIC_POLICY");
        assertThat(jdbc.queryForObject("SELECT total_minor FROM quote WHERE id = ?", Long.class, UUID.fromString(q.get("quoteId").asString())))
                .isEqualTo(27000L);

        int before = quoteCount();
        for (String forged : new String[] {"\"total\":{\"amountMinor\":1,\"currency\":\"EGP\"}", "\"deliveryFee\":0", "\"unitPriceMinor\":1",
                "\"customerId\":\"" + UUID.randomUUID() + "\""}) {
            String body = delivery(slot, address, item(mealA, 1) + "," + item(mealB, 1)).replaceFirst("\\{", "{" + forged + ",");
            assertThat(quote(body).getStatus()).as(forged).isEqualTo(400);
        }
        String forgedLinePrice = delivery(slot, address, "{\"mealId\":\"" + mealA + "\",\"quantity\":1,\"unitPrice\":1}");
        assertThat(quote(forgedLinePrice).getStatus()).isEqualTo(400);
        assertThat(quote(delivery(slot, address, "{\"mealId\":\"" + mealA + "\",\"quantity\":1.5}")).getStatus()).isEqualTo(400);
        assertThat(quoteCount()).as("no quote from forged requests").isEqualTo(before);

        JsonNode pick = created(quote(pickup(slot, item(mealA, 2))));
        assertThat(pick.get("deliveryFee").get("amountMinor").asLong()).as("pickup has no delivery fee").isZero();
        assertThat(pick.get("total").get("amountMinor").asLong()).isEqualTo(24000);
    }

    @Test
    void tax_is_applied_with_half_up_rounding_from_the_policy_snapshot() throws Exception {
        activatePolicy(1400);
        UUID odd = meal(kitchen, 7005);
        UUID oddSlot = slot(kitchen, 8, 10, odd, 5);
        JsonNode q = created(quote(pickup(oddSlot, item(odd, 1))));
        // 7005 * 14% = 980.7 -> 981
        assertThat(q.get("tax").get("amountMinor").asLong()).isEqualTo(981);
        assertThat(q.get("total").get("amountMinor").asLong()).isEqualTo(7986);
    }

    // ---- TC-018: one cook and one slot ----

    @Test
    void mixed_kitchens_unoffered_meals_and_invalid_baskets_are_rejected() throws Exception {
        UUID otherKitchen = TestKitchens.approvedKitchen(jdbc, logins.login(UUID.randomUUID().toString(), "Cook").getPrincipal().id(),
                logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        UUID foreign = meal(otherKitchen, 5000);
        MockHttpServletResponse mixed = quote(pickup(slot, item(mealA, 1) + "," + item(foreign, 1)));
        assertThat(mixed.getStatus()).isEqualTo(422);
        assertThat(mixed.getContentAsString()).contains("MIXED_KITCHENS_OR_SLOT");
        UUID unoffered = meal(kitchen, 5000);
        assertThat(quote(pickup(slot, item(unoffered, 1))).getContentAsString()).contains("MEAL_NOT_OFFERED_IN_SLOT");
        assertThat(quote(pickup(slot, item(mealA, 1) + "," + item(mealA, 1))).getContentAsString()).contains("DUPLICATE_OR_MISSING_MEAL");
        assertThat(quote(pickup(slot, item(mealA, 11))).getStatus()).as("above policy maximum").isEqualTo(422);
        assertThat(quote(pickup(slot, "")).getStatus()).as("empty basket is a validation error").isEqualTo(422);
        assertThat(quote(pickup(UUID.randomUUID(), item(mealA, 1))).getStatus()).isEqualTo(404);
    }

    // ---- TC-021: serviceability without silent conversion ----

    @Test
    void unserviceable_delivery_and_disabled_pickup_are_denied_without_conversion() throws Exception {
        UUID farAddress = address(customer, unservedZone);
        MockHttpServletResponse far = quote(delivery(slot, farAddress, item(mealA, 1)));
        assertThat(far.getStatus()).isEqualTo(422);
        assertThat(far.getContentAsString()).contains("NOT_SERVICEABLE");
        assertThat(quote("{\"items\":[" + item(mealA, 1) + "],\"slotId\":\"" + slot + "\",\"fulfillmentMode\":\"DELIVERY\"}").getContentAsString())
                .contains("REQUIRED_FOR_DELIVERY");
        assertThat(quote(pickup(slot, item(mealA, 1)).replace("}],", "}],\"addressId\":\"" + address + "\",")).getContentAsString())
                .contains("NOT_ALLOWED_FOR_PICKUP");

        String otherSubject = UUID.randomUUID().toString();
        UUID otherCustomer = verifiedCustomer(otherSubject);
        UUID othersAddress = address(otherCustomer, zone);
        assertThat(quote(delivery(slot, othersAddress, item(mealA, 1))).getStatus()).as("another customer's address").isEqualTo(404);

        jdbc.update("UPDATE kitchen SET pickup_enabled = false WHERE id = ?", kitchen);
        MockHttpServletResponse noPickup = quote(pickup(slot, item(mealA, 1)));
        assertThat(noPickup.getStatus()).isEqualTo(422);
        assertThat(noPickup.getContentAsString()).contains("PICKUP_NOT_OFFERED");
        assertThat(quoteCount()).isZero();
    }

    @Test
    void contact_verification_kitchen_state_and_cutoff_are_enforced_by_server_time() throws Exception {
        String unverified = UUID.randomUUID().toString();
        logins.login(unverified, "Customer");
        MockHttpServletResponse r = quote(unverified, pickup(slot, item(mealA, 1)));
        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(r.getContentAsString()).contains("CONTACT_VERIFICATION_REQUIRED");

        UUID soon = slotInMinutes(kitchen, 131, 10, mealA, 5);
        JsonNode q = created(quote(pickup(soon, item(mealA, 1))));
        Instant cutoff = jdbc.queryForObject("SELECT cutoff_at FROM cook_slot WHERE id = ?", Timestamp.class, soon).toInstant();
        Instant accept = Instant.parse(q.get("acceptanceDeadlinePreview").asString());
        assertThat(accept).isBeforeOrEqualTo(cutoff).isBeforeOrEqualTo(clock.instant().plus(Duration.ofMinutes(15)));

        ((MutableClock) clock).advance(Duration.between(clock.instant(), cutoff.minus(Duration.ofMinutes(4))));
        MockHttpServletResponse late = quote(pickup(soon, item(mealA, 1)));
        assertThat(late.getStatus()).isEqualTo(409);
        assertThat(late.getContentAsString()).contains("ACCEPTANCE_WINDOW_TOO_SHORT");
        ((MutableClock) clock).advance(Duration.ofMinutes(5));
        assertThat(quote(pickup(soon, item(mealA, 1))).getContentAsString()).contains("SLOT_CLOSED");
        ((MutableClock) clock).reset();

        jdbc.update("UPDATE kitchen SET operating_state = 'PAUSED' WHERE id = ?", kitchen);
        assertThat(quote(pickup(slot, item(mealA, 1))).getContentAsString()).contains("KITCHEN_UNAVAILABLE");
    }

    // ---- BR-020: no stock hold; expiry and revalidation (TC-020) ----

    @Test
    void quotes_hold_no_stock_and_cannot_exceed_remaining_portions() throws Exception {
        created(quote(pickup(slot, item(mealA, 5))));
        created(quote(pickup(slot, item(mealA, 5))));
        assertThat(jdbc.queryForObject("SELECT reserved + allocated + consumed FROM cook_slot WHERE id = ?", Integer.class, slot)).isZero();
        jdbc.update("UPDATE meal_slot SET reserved = 3 WHERE cook_slot_id = ? AND meal_id = ?", slot, mealA);
        jdbc.update("UPDATE cook_slot SET reserved = 3 WHERE id = ?", slot);
        MockHttpServletResponse r = quote(pickup(slot, item(mealA, 3)));
        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(r.getContentAsString()).contains("STOCK_CHANGED");
        jdbc.update("UPDATE cook_slot SET reserved = 9 WHERE id = ?", slot);
        assertThat(quote(pickup(slot, item(mealB, 2))).getContentAsString()).as("slot aggregate bound").contains("INSUFFICIENT_PORTIONS");
    }

    @Test
    void a_stale_quote_is_refused_with_a_recoverable_reason() throws Exception {
        UUID fresh = UUID.fromString(created(quote(delivery(slot, address, item(mealA, 1)))).get("quoteId").asString());
        QuoteDirectory.ValidQuote valid = quotes.requireValid(customer, fresh);
        assertThat(valid.totalMinor()).isEqualTo(15000);
        assertThat(valid.items()).hasSize(1);

        // Meal repriced (new revision).
        UUID priceQuote = UUID.fromString(created(quote(delivery(slot, address, item(mealB, 1)))).get("quoteId").asString());
        jdbc.update("INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor) VALUES (?, 2, '{}'::jsonb, 13000)", mealB);
        jdbc.update("UPDATE meal SET price_minor = 13000, revision = 2 WHERE id = ?", mealB);
        ApiException repriced = rejection(priceQuote);
        assertThat(repriced.code()).isEqualTo(ErrorCode.PRICE_CHANGED);
        assertThat(repriced.fieldErrors().getFirst().code()).isEqualTo("MEAL_CHANGED");

        // Address edited.
        jdbc.update("UPDATE address SET street = 'Changed street', version = version + 1 WHERE id = ?", address);
        assertThat(rejection(fresh).fieldErrors().getFirst().code()).isEqualTo("ADDRESS_CHANGED");

        // Delivery fee changed for the zone.
        UUID feeQuote = UUID.fromString(created(quote(delivery(slot, address, item(mealA, 1)))).get("quoteId").asString());
        jdbc.update("UPDATE service_zone SET delivery_fee_minor = 3500 WHERE id = ?", zone);
        assertThat(rejection(feeQuote).fieldErrors().getFirst().code()).isEqualTo("TOTAL_CHANGED");

        // Policy replaced.
        UUID policyQuote = UUID.fromString(created(quote(pickup(slot, item(mealA, 1)))).get("quoteId").asString());
        activatePolicy(0);
        assertThat(rejection(policyQuote).fieldErrors().getFirst().code()).isEqualTo("POLICY_CHANGED");

        // Slot paused.
        UUID slotQuote = UUID.fromString(created(quote(pickup(slot, item(mealA, 1)))).get("quoteId").asString());
        jdbc.update("UPDATE cook_slot SET state = 'PAUSED' WHERE id = ?", slot);
        assertThat(rejection(slotQuote).code()).isEqualTo(ErrorCode.SLOT_CLOSED);
        jdbc.update("UPDATE cook_slot SET state = 'OPEN' WHERE id = ?", slot);

        // Expired after five minutes of server time.
        UUID expiring = UUID.fromString(created(quote(pickup(slot, item(mealA, 1)))).get("quoteId").asString());
        quotes.requireValid(customer, expiring);
        ((MutableClock) clock).advance(Duration.ofSeconds(301));
        assertThat(rejection(expiring).code()).isEqualTo(ErrorCode.QUOTE_EXPIRED);

        // Another customer cannot use it.
        UUID other = verifiedCustomer(UUID.randomUUID().toString());
        assertThatThrownBy(() -> quotes.requireValid(other, fresh)).isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).code()).isEqualTo(ErrorCode.NOT_FOUND);
    }

    // ---- policy and immutability ----

    @Test
    void ordering_is_blocked_without_an_active_policy_and_real_policies_need_approval() throws Exception {
        jdbc.update("UPDATE commerce_policy SET status = 'RETIRED' WHERE status = 'ACTIVE'");
        MockHttpServletResponse r = quote(pickup(slot, item(mealA, 1)));
        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(r.getContentAsString()).contains("NO_APPROVED_POLICY");
        Integer next = jdbc.queryForObject("SELECT max(version) + 1 FROM commerce_policy", Integer.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce_policy (version, label, synthetic, status, tax_rate_bps, commission_rate_bps, max_item_quantity,
                                             quote_ttl_seconds, acceptance_window_minutes, min_acceptance_minutes, cancellation_policy_version)
                VALUES (?, 'Real but unapproved', false, 'ACTIVE', 0, 1000, 10, 300, 15, 5, 'v1')""", next))
                .hasMessageContaining("commerce_policy_active_approved_ck");
    }

    @Test
    void quotes_are_immutable() throws Exception {
        UUID q = UUID.fromString(created(quote(pickup(slot, item(mealA, 1)))).get("quoteId").asString());
        assertThatThrownBy(() -> jdbc.update("UPDATE quote SET total_minor = 1 WHERE id = ?", q)).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE quote_item SET unit_price_minor = 1 WHERE quote_id = ?", q)).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM quote WHERE id = ?", q)).hasMessageContaining("immutable");
    }
}
