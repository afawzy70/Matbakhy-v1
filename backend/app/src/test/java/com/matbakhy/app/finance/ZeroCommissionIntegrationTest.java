package com.matbakhy.app.finance;

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
import com.matbakhy.finance.internal.LedgerService;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MB-050 / TC-157 (owner decision D10, 2026-10-13): no Matbakhy commission or platform fee at launch. The server refuses
 * a non-zero commission in any policy it accepts, the database refuses one for a live (non-synthetic) policy, the
 * development policy is 0%, and a launch order carries zero commission from quote to ledger and statement.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class ZeroCommissionIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired LedgerService ledger;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    UUID kitchen;
    UUID meal;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activateLaunchPolicy(0);
        customerSubject = UUID.randomUUID().toString();
        fx.verifyPhone(logins.login(customerSubject, "Customer").getPrincipal().id());
        cookSubject = UUID.randomUUID().toString();
        UUID cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        UUID zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 12000, "كفتة", "Kofta");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(logins.login(customerSubject, "Customer").getPrincipal().id(), zone);
    }

    @AfterEach
    void restore() {
        fx.activatePolicy(0);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private void cook(UUID order, String action, String extra) throws Exception {
        long version = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", "zc-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + version + extra + "}"), logins.login(cookSubject, "Cook")), 200);
    }

    private String staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        return subject;
    }

    private static String policy(int commissionBps) {
        return """
                {"label":"Launch policy","synthetic":true,"taxRateBps":0,"commissionRateBps":%d,"maxItemQuantity":10,"quoteTtlSeconds":300,
                 "acceptanceWindowMinutes":15,"minAcceptanceMinutes":5,"cancellationPolicyVersion":"cancel-v2","termsVersion":"terms-v2"}"""
                .formatted(commissionBps);
    }

    private MockHttpServletResponse newPolicy(int commissionBps, AccountAuthentication who) throws Exception {
        int active = jdbc.queryForObject("SELECT version FROM commerce_policy WHERE status = 'ACTIVE'", Integer.class);
        return call(post("/api/v1/ops/configuration/commerce-policy/versions").content("{\"value\":" + policy(commissionBps)
                + ",\"reason\":\"launch terms D10\",\"expectedVersion\":" + active + "}"), who);
    }

    @Test
    void a_launch_order_carries_zero_commission_from_quote_to_ledger_and_statement() throws Exception {
        AccountAuthentication customer = logins.login(customerSubject, "Customer");
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), customer), 201);
        assertThat(quote.get("total").get("amountMinor").asLong()).as("items 24000 + delivery 3000, no platform fee").isEqualTo(27000);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", "zc-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), customer), 201);
        UUID id = UUID.fromString(order.get("id").asString());
        assertThat(jdbc.queryForObject("SELECT commission_bps_snapshot || '|' || commission_minor FROM customer_order WHERE id = ?", String.class, id))
                .isEqualTo("0|0");
        cook(id, "accept", "");
        cook(id, "prepare", "");
        cook(id, "ready", "");
        cook(id, "dispatch", ",\"deliveryReference\":\"Synthetic rider\"");
        cook(id, "handover", ",\"code\":\"" + ok(call(get("/api/v1/orders/" + id), customer), 200).get("handoverCode").asString() + "\"");
        UUID event = jdbc.queryForObject("SELECT id FROM order_event WHERE order_id = ? AND to_state = 'COMPLETED'", UUID.class, id);
        ledger.postCommission(event, id);
        assertThat(jdbc.queryForObject("""
                SELECT coalesce(sum(amount_minor), 0) FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id WHERE g.order_id = ?""",
                Long.class, id)).as("no commission posted").isZero();

        String today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Cairo"))).toString();
        JsonNode statement = ok(call(get("/api/v1/cook/statements").param("from", today).param("to", today),
                logins.login(cookSubject, "Cook")), 200);
        assertThat(statement.get("summary").get("commissionPosted").get("amountMinor").asLong()).isZero();
        assertThat(statement.get("summary").get("commissionOutstanding").get("amountMinor").asLong()).isZero();
    }

    @Test
    void a_non_zero_commission_is_refused_by_the_server_and_for_live_policies_by_the_database() throws Exception {
        AccountAuthentication writer = logins.loginWithMfa(staff("config.write", "config.read"), "Writer");
        MockHttpServletResponse refused = newPolicy(1000, writer);
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(422);
        assertThat(refused.getContentAsString()).contains("commissionRateBps");
        ok(newPolicy(0, writer), 200);

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO commerce_policy (version, label, synthetic, status, tax_rate_bps, commission_rate_bps, max_item_quantity, quote_ttl_seconds,
                                             acceptance_window_minutes, min_acceptance_minutes, cancellation_policy_version, terms_version)
                VALUES (9001, 'live with commission', false, 'RETIRED', 0, 1000, 10, 300, 15, 5, 'c', 't')""")).hasMessageContaining("commission");
    }

    @Test
    void the_development_policy_has_no_commission() throws Exception {
        String sql = new ClassPathResource("db/devdata/R__dev_commerce_policy.sql").getContentAsString(StandardCharsets.UTF_8);
        assertThat(sql).contains("'ACTIVE', 0, 0, 10,").doesNotContain("1000");
    }
}
