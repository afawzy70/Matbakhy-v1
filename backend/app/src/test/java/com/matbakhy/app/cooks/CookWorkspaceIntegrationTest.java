package com.matbakhy.app.cooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.finance.internal.LedgerService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
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
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MB-022 backend for the cook workspace (TC-122): dashboard, own settings with a cook-controlled pause that cannot
 * clear a staff pause or suspension, and the own statement. Every read and change is scoped to the own kitchen.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class CookWorkspaceIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired LedgerService ledger;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    String otherCookSubject;
    UUID cook;
    UUID kitchen;
    UUID otherKitchen;
    UUID zone;
    UUID meal;
    UUID slot;
    UUID address;
    String reviewerSubject;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        UUID customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        reviewerSubject = UUID.randomUUID().toString();
        UUID reviewer = logins.login(reviewerSubject, "Reviewer").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, cook, reviewer);
        otherCookSubject = UUID.randomUUID().toString();
        otherKitchen = TestKitchens.approvedKitchen(jdbc, logins.login(otherCookSubject, "Other cook").getPrincipal().id(), reviewer);
        zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 12000, "مسقعة", "Moussaka");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(customer, zone);
    }

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "User");
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private static String key() {
        return "ws-" + UUID.randomUUID();
    }

    private long version(UUID order) {
        return jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
    }

    private UUID placeOrder(String mode) throws Exception {
        String target = "DELIVERY".equals(mode) ? ",\"addressId\":\"" + address + "\"" : "";
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"" + mode + "\"" + target + "}"), as(customerSubject)), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", key()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), as(customerSubject)), 201);
        return UUID.fromString(order.get("id").asString());
    }

    private void cookCommand(UUID order, String action, String extra) throws Exception {
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(order) + extra + "}"), as(cookSubject)), 200);
    }

    private JsonNode dashboard() throws Exception {
        return ok(call(get("/api/v1/cook/dashboard"), as(cookSubject)), 200);
    }

    private MockHttpServletResponse settings(String body, long version) throws Exception {
        return call(patch("/api/v1/cook/settings").content("{" + body + (body.isEmpty() ? "" : ",") + "\"expectedVersion\":" + version + "}"),
                as(cookSubject));
    }

    private long kitchenVersion() {
        return jdbc.queryForObject("SELECT version FROM kitchen WHERE id = ?", Long.class, kitchen);
    }

    // ---- dashboard ----

    @Test
    void dashboard_shows_own_queue_deadlines_slot_totals_and_settings_only() throws Exception {
        UUID first = placeOrder("DELIVERY");
        UUID second = placeOrder("DELIVERY");
        cookCommand(second, "accept", "");
        JsonNode d = dashboard();
        assertThat(d.get("serverTime").asString()).isNotBlank();
        assertThat(d.get("kitchen").get("kitchenId").asString()).isEqualTo(kitchen.toString());
        assertThat(d.get("kitchen").get("operatingState").asString()).isEqualTo("ACTIVE");
        assertThat(d.get("kitchen").has("contactPhone")).isFalse();
        assertThat(d.get("activeCounts").get("PENDING_ACCEPTANCE").asInt()).isEqualTo(1);
        assertThat(d.get("activeCounts").get("ACCEPTED").asInt()).isEqualTo(1);
        assertThat(d.get("activeCounts").get("READY").asInt()).isZero();
        assertThat(d.get("pendingAcceptance").size()).isEqualTo(1);
        assertThat(d.get("pendingAcceptance").get(0).get("orderId").asString()).isEqualTo(first.toString());
        assertThat(d.get("pendingAcceptance").get(0).get("acceptBy").asString()).isNotBlank();
        JsonNode s = d.get("upcomingSlots").get(0);
        assertThat(s.get("slotId").asString()).isEqualTo(slot.toString());
        assertThat(s.get("reserved").asInt()).isEqualTo(2);
        assertThat(s.get("allocated").asInt()).isEqualTo(2);
        assertThat(s.get("maxPortions").asInt()).isEqualTo(20);

        JsonNode other = ok(call(get("/api/v1/cook/dashboard"), as(otherCookSubject)), 200);
        assertThat(other.get("kitchen").get("kitchenId").asString()).isEqualTo(otherKitchen.toString());
        assertThat(other.get("pendingAcceptance").size()).isZero();
        assertThat(other.get("upcomingSlots").size()).isZero();
        assertThat(call(get("/api/v1/cook/dashboard"), as(customerSubject)).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/cook/orders/" + first), as(otherCookSubject)).getStatus()).as("other kitchen's order").isEqualTo(404);
        assertThat(call(post("/api/v1/cook/orders/" + first + "/accept").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(first) + "}"), as(otherCookSubject)).getStatus()).isEqualTo(404);
    }

    // ---- settings ----

    @Test
    void cook_pause_and_resume_stop_and_restore_new_sales_but_not_a_staff_pause() throws Exception {
        JsonNode paused = ok(settings("\"paused\":true", kitchenVersion()), 200);
        assertThat(paused.get("operatingState").asString()).isEqualTo("PAUSED");
        assertThat(paused.get("pausedBy").asString()).isEqualTo("COOK");
        assertThat(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), as(customerSubject)).getStatus())
                .as("a paused kitchen does not sell").isNotEqualTo(201);
        JsonNode resumed = ok(settings("\"paused\":false", paused.get("version").asLong()), 200);
        assertThat(resumed.get("operatingState").asString()).isEqualTo("ACTIVE");
        assertThat(resumed.get("pausedBy").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'kitchen.settings' AND object_id = ?", Integer.class,
                kitchen.toString())).isEqualTo(2);

        // A staff pause cannot be lifted by the cook; staff restore it.
        String ops = UUID.randomUUID().toString();
        UUID opsId = logins.login(ops, "Ops").getPrincipal().id();
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'cook.review', 'test grant')", UUID.randomUUID(), opsId);
        ok(call(post("/api/v1/ops/kitchens/" + kitchen + "/operating-state").header("Idempotency-Key", key())
                .content("{\"action\":\"PAUSE\",\"reason\":\"quality check\",\"expectedVersion\":" + kitchenVersion() + "}"), logins.loginWithMfa(ops, "Ops")), 200);
        assertThat(dashboard().get("kitchen").get("pausedBy").asString()).isEqualTo("STAFF");
        assertThat(settings("\"paused\":false", kitchenVersion()).getStatus()).isEqualTo(409);
        ok(call(post("/api/v1/ops/kitchens/" + kitchen + "/operating-state").header("Idempotency-Key", key())
                .content("{\"action\":\"SUSPEND\",\"reason\":\"complaint review\",\"expectedVersion\":" + kitchenVersion() + "}"), logins.loginWithMfa(ops, "Ops")), 200);
        assertThat(settings("\"paused\":false", kitchenVersion()).getStatus()).as("cannot self-clear suspension").isEqualTo(409);
        assertThat(settings("\"paused\":true", kitchenVersion()).getStatus()).isEqualTo(409);
        assertThat(dashboard().get("kitchen").get("operatingState").asString()).isEqualTo("SUSPENDED");
        assertThat(settings("\"pickupEnabled\":true", kitchenVersion()).getStatus()).as("other settings still editable").isEqualTo(200);
    }

    @Test
    void settings_validate_serviceability_zones_and_versions_and_update_the_public_profile() throws Exception {
        long v = kitchenVersion();
        assertThat(settings("\"pickupEnabled\":false,\"deliveryZones\":[{\"zoneId\":\"" + zone + "\",\"deliveryEnabled\":false}]", v).getStatus())
                .as("must stay serviceable").isEqualTo(422);
        assertThat(settings("\"deliveryZones\":[{\"zoneId\":\"" + UUID.randomUUID() + "\",\"deliveryEnabled\":true}]", v).getStatus())
                .as("only approved zones").isEqualTo(422);
        assertThat(settings("\"description\":\"x\"", v - 1).getStatus()).isEqualTo(409);
        assertThat(settings("\"description\":\"   \"", v).getStatus()).isEqualTo(422);
        JsonNode changed = ok(settings("\"description\":\"New menu every Friday\",\"pickupEnabled\":true,\"deliveryZones\":[{\"zoneId\":\"" + zone
                + "\",\"deliveryEnabled\":false}]", v), 200);
        assertThat(changed.get("pickupEnabled").asBoolean()).isTrue();
        assertThat(changed.get("zones").get(0).get("deliveryEnabled").asBoolean()).isFalse();
        assertThat(changed.get("version").asLong()).isGreaterThan(v);
        String slug = changed.get("publicSlug").asString();
        JsonNode profile = json.readTree(mvc.perform(get("/api/v1/kitchens/" + slug)).andReturn().getResponse().getContentAsString());
        assertThat(profile.get("description").asString()).isEqualTo("New menu every Friday");
        assertThat(profile.get("deliveryZoneIds").size()).isZero();
        assertThat(profile.get("pickupEnabled").asBoolean()).isTrue();
        assertThat(call(patch("/api/v1/cook/settings").content("{\"paused\":true,\"expectedVersion\":0}"), as(customerSubject)).getStatus())
                .isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT display_name FROM kitchen WHERE id = ?", String.class, kitchen))
                .as("approved identity unchanged").isEqualTo(dashboard().get("kitchen").get("displayName").asString());
    }

    // ---- statement ----

    @Test
    void statement_lists_own_completed_orders_with_cash_position_and_posted_commission() throws Exception {
        fx.activatePolicy(0);
        UUID order = placeOrder("PICKUP");
        cookCommand(order, "accept", "");
        cookCommand(order, "prepare", "");
        cookCommand(order, "ready", "");
        String code = ok(call(get("/api/v1/orders/" + order), as(customerSubject)), 200).get("handoverCode").asString();
        cookCommand(order, "handover", ",\"code\":\"" + code + "\"");
        UUID pendingOrder = placeOrder("DELIVERY");

        JsonNode before = ok(call(get("/api/v1/cook/statements"), as(cookSubject)), 200);
        assertThat(before.get("includes").toString()).contains("COMMISSION").doesNotContain("REFUND").doesNotContain("REMITTANCE");
        assertThat(before.get("items").size()).isEqualTo(1);
        JsonNode line = before.get("items").get(0);
        assertThat(line.get("orderId").asString()).isEqualTo(order.toString());
        assertThat(line.get("orderTotal").get("amountMinor").asLong()).isEqualTo(24000);
        assertThat(line.get("cashState").asString()).isEqualTo("DUE");
        assertThat(line.get("commissionState").asString()).isEqualTo("PENDING_POSTING");
        assertThat(before.get("summary").get("cashOutstanding").get("amountMinor").asLong()).isEqualTo(24000);
        assertThat(before.get("summary").get("commissionPending").asInt()).isEqualTo(1);

        UUID event = jdbc.queryForObject("SELECT id FROM order_event WHERE order_id = ? AND to_state = 'COMPLETED'", UUID.class, order);
        ledger.postCommission(event, order);
        JsonNode after = ok(call(get("/api/v1/cook/statements"), as(cookSubject)), 200);
        assertThat(after.get("items").get(0).get("commissionState").asString()).isEqualTo("POSTED");
        assertThat(after.get("items").get(0).get("commission").get("amountMinor").asLong()).isEqualTo(2400);
        assertThat(after.get("summary").get("commissionPosted").get("amountMinor").asLong()).isEqualTo(2400);
        assertThat(after.get("summary").get("completedOrders").asInt()).isEqualTo(1);
        assertThat(after.toString()).doesNotContain(pendingOrder.toString());

        assertThat(ok(call(get("/api/v1/cook/statements"), as(otherCookSubject)), 200).get("items").size()).as("own kitchen only").isZero();
        String today = LocalDate.now(ZoneId.of("Africa/Cairo")).toString();
        String yesterday = LocalDate.now(ZoneId.of("Africa/Cairo")).minusDays(1).toString();
        assertThat(ok(call(get("/api/v1/cook/statements").param("from", yesterday).param("to", yesterday), as(cookSubject)), 200)
                .get("items").size()).isZero();
        assertThat(call(get("/api/v1/cook/statements").param("from", today).param("to", yesterday), as(cookSubject)).getStatus()).isEqualTo(422);
        assertThat(call(get("/api/v1/cook/statements").param("from", "11/10/2026"), as(cookSubject)).getStatus()).isEqualTo(422);
        assertThat(call(get("/api/v1/cook/statements"), as(customerSubject)).getStatus()).isEqualTo(403);
    }

    @Test
    void statement_cursor_is_bound_to_its_period() throws Exception {
        for (int i = 0; i < 2; i++) {
            UUID order = placeOrder("PICKUP");
            cookCommand(order, "accept", "");
            cookCommand(order, "prepare", "");
            cookCommand(order, "ready", "");
            String code = ok(call(get("/api/v1/orders/" + order), as(customerSubject)), 200).get("handoverCode").asString();
            cookCommand(order, "handover", ",\"code\":\"" + code + "\"");
        }
        JsonNode page1 = ok(call(get("/api/v1/cook/statements").param("limit", "1"), as(cookSubject)), 200);
        assertThat(page1.get("hasMore").asBoolean()).isTrue();
        assertThat(page1.get("summary").get("completedOrders").asInt()).as("summary covers the whole period").isEqualTo(2);
        String cursor = page1.get("nextCursor").asString();
        JsonNode page2 = ok(call(get("/api/v1/cook/statements").param("limit", "1").param("cursor", cursor), as(cookSubject)), 200);
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("items").get(0).get("orderId").asString()).isNotEqualTo(page1.get("items").get(0).get("orderId").asString());
        String from = LocalDate.now(ZoneId.of("Africa/Cairo")).minusDays(3).toString();
        assertThat(call(get("/api/v1/cook/statements").param("from", from).param("cursor", cursor), as(cookSubject)).getStatus()).isEqualTo(422);
    }
}
