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
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * TC-042 on PostgreSQL: configuration changes are versioned, reasoned, recorded append-only and audited; placed orders
 * keep their snapshots, stale quotes fail revalidation, new quotes use the new version; the ordering kill switch and
 * the live approval guard for real policies. TC-043 (self-escalation, last administrator) is StaffAccessIntegrationTest.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class ConfigurationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired io.micrometer.core.instrument.MeterRegistry meters;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    String writer;
    String reader;
    UUID zone;
    UUID meal;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        UUID customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        UUID cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        writer = staff("config.write", "config.read");
        reader = staff("config.read");
        UUID kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(staff("cook.review"), "Reviewer").getPrincipal().id());
        zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 10000, "فول", "Foul");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(customer, zone);
    }

    @AfterEach
    void reopenOrdering() {
        jdbc.update("UPDATE feature_flag SET enabled = true WHERE key = 'ordering-enabled'");
    }

    private String staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        return subject;
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private AccountAuthentication customer() {
        return logins.login(customerSubject, "Customer");
    }

    private JsonNode quote() throws Exception {
        return ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), customer()), 201);
    }

    private MockHttpServletResponse place(JsonNode quote) throws Exception {
        return call(post("/api/v1/orders").header("Idempotency-Key", "cfg-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), quote.get("termsVersion").asString(),
                        quote.get("cancellationPolicyVersion").asString())), customer());
    }

    private MockHttpServletResponse change(String key, String value, String extra, long expectedVersion, AccountAuthentication who) throws Exception {
        return call(post("/api/v1/ops/configuration/" + key + "/versions").content("{\"value\":" + value + ",\"reason\":\"pilot adjustment\"" + extra
                + ",\"expectedVersion\":" + expectedVersion + "}"), who);
    }

    private long zoneVersion() {
        return jdbc.queryForObject("SELECT version FROM service_zone WHERE id = ?", Long.class, zone);
    }

    private int activePolicy() {
        return jdbc.queryForObject("SELECT version FROM commerce_policy WHERE status = 'ACTIVE'", Integer.class);
    }

    private static String policy(boolean synthetic, int taxBps) {
        return """
                {"label":"Pilot policy","synthetic":%s,"taxRateBps":%d,"commissionRateBps":1000,"maxItemQuantity":20,"quoteTtlSeconds":300,
                 "acceptanceWindowMinutes":15,"minAcceptanceMinutes":5,"cancellationPolicyVersion":"cancel-v2","termsVersion":"terms-v2"}"""
                .formatted(synthetic, taxBps);
    }

    @Test
    void a_fee_change_keeps_placed_orders_invalidates_stale_quotes_and_applies_to_new_quotes() throws Exception {
        JsonNode placedQuote = quote();
        JsonNode order = ok(place(placedQuote), 201);
        JsonNode stale = quote();
        assertThat(stale.get("deliveryFee").get("amountMinor").asLong()).isEqualTo(3000);

        AccountAuthentication mfa = logins.loginWithMfa(writer, "Writer");
        assertThat(change("zone:" + zone, "{\"active\":true,\"deliveryFeeMinor\":4500}", "", zoneVersion(), logins.login(writer, "Writer")).getStatus())
                .as("step-up required").isEqualTo(403);
        assertThat(change("zone:" + zone, "{\"active\":true,\"deliveryFeeMinor\":4500}", "", zoneVersion(), logins.loginWithMfa(reader, "Reader")).getStatus())
                .as("read-only staff").isEqualTo(403);
        assertThat(change("zone:" + zone, "{\"active\":true,\"deliveryFeeMinor\":4500}", "", zoneVersion() + 3, mfa).getStatus()).isEqualTo(409);
        assertThat(change("zone:" + zone, "{\"active\":true,\"deliveryFeeMinor\":-1}", "", zoneVersion(), mfa).getStatus()).isEqualTo(422);
        // Browser clients percent-encode the key (zone%3A...); the server decodes it.
        JsonNode changed = ok(call(post(java.net.URI.create("/api/v1/ops/configuration/zone%3A" + zone + "/versions")).content(
                "{\"value\":{\"active\":true,\"deliveryFeeMinor\":4500},\"reason\":\"pilot adjustment\",\"expectedVersion\":" + zoneVersion() + "}"),
                mfa), 200);
        assertThat(changed.get("version").asLong()).isEqualTo(zoneVersion());

        JsonNode view = ok(call(get("/api/v1/orders/" + order.get("id").asString()), customer()), 200);
        assertThat(view.get("deliveryFee").get("amountMinor").asLong()).as("placed order keeps its snapshot").isEqualTo(3000);
        MockHttpServletResponse stalePlacement = place(stale);
        assertThat(stalePlacement.getStatus()).as("stale quote no longer valid").isEqualTo(409);
        assertThat(stalePlacement.getContentAsString()).contains("TOTAL_CHANGED");
        assertThat(quote().get("deliveryFee").get("amountMinor").asLong()).as("new quote uses the new fee").isEqualTo(4500);

        JsonNode config = ok(call(get("/api/v1/ops/configuration"), logins.login(reader, "Reader")), 200);
        assertThat(config.get("changes").get(0).get("key").asString()).isEqualTo("zone:" + zone);
        assertThat(config.get("changes").get(0).get("value").get("deliveryFeeMinor").asLong()).isEqualTo(4500);
        assertThat(call(get("/api/v1/ops/configuration"), customer()).getStatus()).isEqualTo(403);
        assertThatThrownBy(() -> jdbc.update("UPDATE config_change SET reason = 'edited'")).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'config.change' AND object_id = ?", Integer.class, "zone:" + zone))
                .isEqualTo(1);
    }

    @Test
    void a_new_policy_version_applies_to_new_quotes_only_and_a_real_policy_needs_an_approval_reference() throws Exception {
        JsonNode stale = quote();
        AccountAuthentication mfa = logins.loginWithMfa(writer, "Writer");
        assertThat(change("commerce-policy", policy(false, 1400), "", activePolicy(), mfa).getStatus()).as("live approval guard").isEqualTo(422);
        assertThat(change("commerce-policy", policy(true, 1400), ",\"effectiveAt\":\"2099-01-01T00:00:00Z\"", activePolicy(), mfa).getStatus())
                .as("scheduled changes not supported").isEqualTo(422);
        JsonNode synthetic = ok(change("commerce-policy", policy(true, 1400), "", activePolicy(), mfa), 200);
        assertThat(synthetic.get("version").asInt()).isEqualTo(activePolicy());
        MockHttpServletResponse stalePlacement = place(stale);
        assertThat(stalePlacement.getContentAsString()).contains("POLICY_CHANGED");
        JsonNode fresh = quote();
        assertThat(fresh.get("tax").get("amountMinor").asLong()).as("14% of 10000 + 3000").isEqualTo(1820);
        assertThat(fresh.get("termsVersion").asString()).isEqualTo("terms-v2");

        JsonNode real = ok(change("commerce-policy", policy(false, 1400), ",\"approvalReference\":\"OWNER-O04-2026-10-11\"", activePolicy(), mfa), 200);
        assertThat(jdbc.queryForObject("SELECT approved_at IS NOT NULL AND NOT synthetic FROM commerce_policy WHERE version = ?", Boolean.class,
                real.get("version").asInt())).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM commerce_policy WHERE status = 'ACTIVE'", Integer.class)).isEqualTo(1);
        assertThat(change("commerce-policy", policy(true, 0), "", activePolicy() - 1, mfa).getStatus()).isEqualTo(409);
        assertThat(change("unknown-key", "{}", "", 0, mfa).getStatus()).isEqualTo(404);
    }

    @Test
    void the_ordering_switch_stops_new_quotes_and_placements_but_not_fulfilment() throws Exception {
        JsonNode pending = ok(place(quote()), 201);
        JsonNode unplaced = quote();
        AccountAuthentication mfa = logins.loginWithMfa(writer, "Writer");
        long v = jdbc.queryForObject("SELECT version FROM feature_flag WHERE key = 'ordering-enabled'", Long.class);
        ok(change("flag:ordering-enabled", "{\"enabled\":false}", "", v, mfa), 200);
        MockHttpServletResponse blocked = call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), customer());
        assertThat(blocked.getStatus()).isEqualTo(409);
        assertThat(blocked.getContentAsString()).contains("ORDERING_PAUSED");
        assertThat(place(unplaced).getContentAsString()).contains("ORDERING_PAUSED");
        long orderVersion = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?::uuid", Long.class, pending.get("id").asString());
        ok(call(post("/api/v1/cook/orders/" + pending.get("id").asString() + "/accept").header("Idempotency-Key", "cfg-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + orderVersion + "}"), logins.login(cookSubject, "Cook")), 200);
        assertThat(change("flag:no-such-flag", "{\"enabled\":true}", "", 0, mfa).getStatus()).isEqualTo(404);
        ok(change("flag:ordering-enabled", "{\"enabled\":true}", "", v + 1, mfa), 200);
        quote();
    }

    /**
     * MB-039 (AUD-01): audit-write failure, injected with a test-only trigger. A critical change whose audit row cannot be
     * written must not commit (no unaudited change, no phantom success). A denial whose audit row cannot be written must
     * still be refused.
     */
    @Test
    void a_failed_audit_write_rolls_back_the_change_and_never_turns_a_denial_into_success() throws Exception {
        String key = "zone:" + zone;
        long before = zoneVersion();
        int changesBefore = jdbc.queryForObject("SELECT count(*) FROM config_change", Integer.class);
        AccountAuthentication mfa = logins.loginWithMfa(writer, "Writer");
        double failuresBefore = meters.counter("matbakhy.audit.write.failures", "kind", "denial").count();
        String customerId = customer().getPrincipal().id().toString();
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION mb039_fail_audit() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.object_id = '%s' OR NEW.actor_id = '%s'::uuid THEN RAISE EXCEPTION 'forced audit write failure'; END IF;
                    RETURN NEW;
                END $$""".formatted(key, customerId));
        jdbc.execute("CREATE TRIGGER mb039_fail_audit BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION mb039_fail_audit()");
        MockHttpServletResponse failed;
        MockHttpServletResponse denied;
        try {
            failed = change(key, "{\"active\":true,\"deliveryFeeMinor\":4500}", "", before, mfa);
            denied = change(key, "{\"active\":true,\"deliveryFeeMinor\":9900}", "", before, customer());
        } finally {
            jdbc.execute("DROP TRIGGER mb039_fail_audit ON audit_event");
            jdbc.execute("DROP FUNCTION mb039_fail_audit()");
        }
        assertThat(failed.getStatus()).as(failed.getContentAsString()).isGreaterThanOrEqualTo(500);
        assertThat(failed.getContentAsString()).contains("requestId").doesNotContain("forced audit write failure");
        assertThat(zoneVersion()).as("the change did not commit").isEqualTo(before);
        assertThat(jdbc.queryForObject("SELECT delivery_fee_minor FROM service_zone WHERE id = ?", Long.class, zone)).isEqualTo(3000);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM config_change", Integer.class)).isEqualTo(changesBefore);
        assertThat(denied.getStatus()).as("still refused with the ordinary denial").isEqualTo(403);
        assertThat(denied.getContentAsString()).contains("FORBIDDEN");
        assertThat(meters.counter("matbakhy.audit.write.failures", "kind", "denial").count()).isEqualTo(failuresBefore + 1);
        assertThat(jdbc.queryForObject("SELECT delivery_fee_minor FROM service_zone WHERE id = ?", Long.class, zone)).isEqualTo(3000);

        // Once the audit store works again, the same change succeeds and is audited.
        ok(change(key, "{\"active\":true,\"deliveryFeeMinor\":4500}", "", before, mfa), 200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'config.change' AND object_id = ?", Integer.class, key)).isEqualTo(1);
    }
}
