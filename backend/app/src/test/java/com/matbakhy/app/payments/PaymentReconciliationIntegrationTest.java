package com.matbakhy.app.payments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.notifications.internal.OutboxDispatcher;
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
 * MB-052 (owner decision D16): the kitchen's payment profile and its staff-approved recipient, the kitchen's
 * "not received" / wrong-amount reports (TC-161), and the refund obligation when a kitchen cannot fulfil after reporting
 * receipt (TC-162). Staff can decide on closed orders with a reason but can never mark money as received.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class PaymentReconciliationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired OutboxDispatcher dispatcher;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    String financeSubject;
    UUID kitchen;
    UUID meal;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activateLaunchPolicy(0);
        customerSubject = UUID.randomUUID().toString();
        UUID customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        UUID cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        UUID zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 12000, "كفتة", "Kofta");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(customer, zone);
        financeSubject = staff("finance.verify", "finance.read");
    }

    @AfterEach
    void restore() {
        fx.activatePolicy(0);
    }

    private String staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        return subject;
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

    private JsonNode setProfile(String identifier) throws Exception {
        long v = jdbc.query("SELECT version FROM kitchen_payment_profile WHERE kitchen_id = ?", rs -> rs.next() ? rs.getLong(1) : 0L, kitchen);
        return ok(call(put("/api/v1/cook/payment-profile").content("""
                {"expectedVersion":%d,"depositBps":5000,"codMaxTotalMinor":20000,
                 "recipient":{"method":"INSTAPAY","identifier":"%s","name":"Synthetic Kitchen"}}""".formatted(v, identifier)), as(cookSubject)), 200);
    }

    private MockHttpServletResponse approve(AccountAuthentication who) throws Exception {
        long v = jdbc.queryForObject("SELECT version FROM kitchen_payment_profile WHERE kitchen_id = ?", Long.class, kitchen);
        return call(post("/api/v1/ops/kitchens/" + kitchen + "/payment-recipient/approval").content("""
                {"expectedVersion":%d,"reason":"recipient name matches the approved cook application"}""".formatted(v)), who);
    }

    private MockHttpServletResponse quote(String method) throws Exception {
        return call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\",\"paymentMethod\":\"" + method + "\"}"), as(customerSubject));
    }

    private UUID depositOrder() throws Exception {
        JsonNode q = ok(quote("DEPOSIT"), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", "rec-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(q.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), as(customerSubject)), 201);
        return UUID.fromString(order.get("id").asString());
    }

    private long paymentVersion(UUID order) {
        return jdbc.queryForObject("SELECT version FROM order_payment WHERE order_id = ?", Long.class, order);
    }

    private String paymentState(UUID order) {
        return jdbc.queryForObject("SELECT state FROM order_payment WHERE order_id = ?", String.class, order);
    }

    private void approvedRecipient() throws Exception {
        setProfile("synthetic.kitchen@instapay");
        ok(approve(logins.loginWithMfa(financeSubject, "Finance")), 200);
    }

    @Test
    void a_new_or_changed_recipient_needs_staff_approval_with_recent_mfa_before_deposits_are_offered() throws Exception {
        JsonNode profile = setProfile("synthetic.kitchen@instapay");
        assertThat(profile.get("recipientStatus").asString()).isEqualTo("NONE");
        assertThat(quote("DEPOSIT").getContentAsString()).contains("DEPOSIT_NOT_OFFERED");

        assertThat(ok(call(get("/api/v1/ops/payment-recipients"), logins.login(financeSubject, "Finance")), 200).get("items").toString())
                .contains(kitchen.toString(), "synthetic.kitchen@instapay");
        assertThat(call(get("/api/v1/ops/payment-recipients"), as(cookSubject)).getStatus()).isEqualTo(403);
        assertThat(approve(as(cookSubject)).getStatus()).as("the cook cannot approve").isEqualTo(403);
        assertThat(approve(logins.login(financeSubject, "Finance")).getStatus()).as("recent MFA required").isEqualTo(403);
        JsonNode approved = ok(approve(logins.loginWithMfa(financeSubject, "Finance")), 200);
        assertThat(approved.get("recipientStatus").asString()).isEqualTo("APPROVED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'payment.recipient_approve' AND object_id = ?",
                Integer.class, kitchen.toString())).isEqualTo(1);
        ok(quote("DEPOSIT"), 201);
        assertThat(ok(call(get("/api/v1/ops/payment-recipients"), logins.login(financeSubject, "Finance")), 200).get("items").toString())
                .doesNotContain(kitchen.toString());

        JsonNode changed = setProfile("another.account@instapay");
        assertThat(changed.get("recipientStatus").asString()).as("a new recipient needs a new approval").isEqualTo("NONE");
        assertThat(quote("DEPOSIT").getContentAsString()).contains("DEPOSIT_NOT_OFFERED");
        assertThat(ok(call(get("/api/v1/cook/payment-profile"), as(cookSubject)), 200).get("recipient").get("identifier").asString())
                .isEqualTo("another.account@instapay");
    }

    @Test
    void the_kitchen_reports_not_received_or_a_wrong_amount_and_staff_can_never_mark_money_received() throws Exception {
        approvedRecipient();
        UUID order = depositOrder();
        ok(call(post("/api/v1/orders/" + order + "/payment/transfer-report").content("{}"), as(customerSubject)), 200);
        MockHttpServletResponse noAmount = call(post("/api/v1/cook/orders/" + order + "/payment/problem").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"kind\":\"AMOUNT_MISMATCH\"}"), as(cookSubject));
        assertThat(noAmount.getStatus()).as("a mismatch states the amount seen").isEqualTo(422);
        String otherCook = UUID.randomUUID().toString();
        TestKitchens.approvedKitchen(jdbc, logins.login(otherCook, "Cook").getPrincipal().id(), logins.login(UUID.randomUUID().toString(), "R").getPrincipal().id());
        assertThat(call(post("/api/v1/cook/orders/" + order + "/payment/problem").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"kind\":\"NOT_RECEIVED\"}"), as(otherCook)).getStatus()).isEqualTo(404);
        JsonNode disputed = ok(call(post("/api/v1/cook/orders/" + order + "/payment/problem").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"kind\":\"NOT_RECEIVED\"}"), as(cookSubject)), 200);
        assertThat(disputed.get("state").asString()).isEqualTo("DISPUTED");

        assertThat(call(get("/api/v1/ops/payments").param("state", "DISPUTED"), as(cookSubject)).getStatus()).isEqualTo(403);
        JsonNode queue = ok(call(get("/api/v1/ops/payments").param("state", "DISPUTED"), logins.login(financeSubject, "Finance")), 200);
        assertThat(queue.get("items").toString()).contains(order.toString(), "NOT_RECEIVED");
        MockHttpServletResponse fabricated = call(post("/api/v1/ops/orders/" + order + "/payment/decision").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"outcome\":\"RECEIVED\",\"reason\":\"customer sent a screenshot\"}"),
                logins.loginWithMfa(financeSubject, "Finance"));
        assertThat(fabricated.getStatus()).as("staff cannot mark funds received").isIn(400, 422);
        MockHttpServletResponse activeDecision = call(post("/api/v1/ops/orders/" + order + "/payment/decision").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"outcome\":\"REFUND_OWED\",\"reason\":\"kitchen says nothing arrived\"}"),
                logins.loginWithMfa(financeSubject, "Finance"));
        assertThat(activeDecision.getStatus()).as("decisions only once the order has ended").isEqualTo(409);

        // The money turns up after all: only the kitchen can say so.
        long v = paymentVersion(order);
        JsonNode received = ok(call(post("/api/v1/cook/orders/" + order + "/payment/receipt").content(
                "{\"expectedVersion\":" + v + ",\"amountMinor\":13500}"), as(cookSubject)), 200);
        assertThat(received.get("state").asString()).isEqualTo("KITCHEN_RECEIPT_REPORTED");
    }

    @Test
    void a_kitchen_that_cannot_fulfil_after_reporting_receipt_owes_a_refund_which_it_reports_as_sent() throws Exception {
        approvedRecipient();
        UUID order = depositOrder();
        ok(call(post("/api/v1/cook/orders/" + order + "/payment/receipt").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"amountMinor\":13500,\"reference\":\"IPN-1\"}"), as(cookSubject)), 200);
        long ov = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
        ok(call(post("/api/v1/cook/orders/" + order + "/reject").header("Idempotency-Key", "rec-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + ov + ",\"reasonCode\":\"INGREDIENT_UNAVAILABLE\"}"), as(cookSubject)), 200);
        assertThat(paymentState(order)).isEqualTo("RECONCILE_PENDING");

        AccountAuthentication finance = logins.loginWithMfa(financeSubject, "Finance");
        MockHttpServletResponse noReason = call(post("/api/v1/ops/orders/" + order + "/payment/decision").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"outcome\":\"REFUND_OWED\"}"), finance);
        assertThat(noReason.getStatus()).isIn(400, 422);
        JsonNode owed = ok(call(post("/api/v1/ops/orders/" + order + "/payment/decision").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"outcome\":\"REFUND_OWED\",\"reason\":\"kitchen rejected after reporting the deposit\"}"),
                finance), 200);
        assertThat(owed.get("state").asString()).isEqualTo("REFUND_OWED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'payment.decision' AND object_id = ?",
                Integer.class, order.toString())).isEqualTo(1);
        assertThat(ok(call(get("/api/v1/orders/" + order), as(customerSubject)), 200).get("payment").get("state").asString()).isEqualTo("REFUND_OWED");

        MockHttpServletResponse noRef = call(post("/api/v1/cook/orders/" + order + "/payment/refund").content(
                "{\"expectedVersion\":" + paymentVersion(order) + "}"), as(cookSubject));
        assertThat(noRef.getStatus()).as("a refund report needs the transfer reference").isIn(400, 422);
        JsonNode refunded = ok(call(post("/api/v1/cook/orders/" + order + "/payment/refund").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"reference\":\"IPN-REFUND-7\"}"), as(cookSubject)), 200);
        assertThat(refunded.get("state").asString()).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT string_agg(to_state || ':' || actor_type, ',' ORDER BY occurred_at) FROM payment_event WHERE order_id = ?",
                String.class, order)).isEqualTo("WAITING_FOR_DEPOSIT:CUSTOMER,KITCHEN_RECEIPT_REPORTED:COOK,RECONCILE_PENDING:COOK,REFUND_OWED:STAFF,REFUNDED:COOK");
        assertThat(jdbc.queryForObject("SELECT state FROM customer_order WHERE id = ?", String.class, order)).isEqualTo("REJECTED");
    }

    private String notices(String subject, UUID order) {
        UUID user = logins.login(subject, "User").getPrincipal().id();
        return String.join(",", jdbc.queryForList("SELECT type || '/' || (params->>'state') FROM notification WHERE recipient_id = ? AND order_id = ? "
                + "AND type LIKE 'PAYMENT_%' ORDER BY created_at, type", String.class, user, order));
    }

    @Test
    void payment_changes_reach_the_other_party_as_in_app_notifications_once() throws Exception {
        approvedRecipient();
        UUID order = depositOrder();
        ok(call(post("/api/v1/orders/" + order + "/payment/transfer-report").content("{}"), as(customerSubject)), 200);
        ok(call(post("/api/v1/orders/" + order + "/payment/transfer-report").content("{}"), as(customerSubject)), 200);
        ok(call(post("/api/v1/cook/orders/" + order + "/payment/problem").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"kind\":\"NOT_RECEIVED\"}"), as(cookSubject)), 200);
        ok(call(post("/api/v1/cook/orders/" + order + "/payment/receipt").content(
                "{\"expectedVersion\":" + paymentVersion(order) + ",\"amountMinor\":13500}"), as(cookSubject)), 200);
        while (dispatcher.dispatchOnce("test-" + UUID.randomUUID(), 50) > 0) {
            // drain
        }
        assertThat(notices(cookSubject, order)).as("the kitchen hears that the customer reported a transfer, once")
                .isEqualTo("PAYMENT_CUSTOMER_TRANSFER_REPORTED/PAYMENT_CUSTOMER_TRANSFER_REPORTED");
        assertThat(notices(customerSubject, order)).as("the customer hears the kitchen's reports")
                .isEqualTo("PAYMENT_DISPUTED/PAYMENT_DISPUTED,PAYMENT_KITCHEN_RECEIPT_REPORTED/PAYMENT_KITCHEN_RECEIPT_REPORTED");
        JsonNode inbox = ok(call(get("/api/v1/notifications"), as(customerSubject)), 200);
        assertThat(inbox.toString()).contains("PAYMENT_DISPUTED").doesNotContain("instapay", "13500");
    }

    private String paymentCases(UUID order) {
        return String.join(",", jdbc.queryForList(
                "SELECT category || '/' || severity || '/' || state FROM support_case WHERE order_id = ? AND category = 'PAYMENT' ORDER BY created_at",
                String.class, order));
    }

    /**
     * Owner direction (mvp-direct-deposit-payment): a kitchen's problem report or a refund obligation opens a support case,
     * once per order, in the same transaction as the payment change; the customer sees it among their cases, staff in the
     * queue, another customer never. The case holds no account or amount details.
     */
    @Test
    void a_dispute_or_refund_obligation_opens_one_payment_support_case_for_the_order() throws Exception {
        approvedRecipient();
        UUID order = depositOrder();
        ok(call(post("/api/v1/orders/" + order + "/payment/transfer-report").content("{\"reference\":\"SYN-REF-1\"}"), as(customerSubject)), 200);
        assertThat(paymentCases(order)).as("no case before a problem").isEmpty();
        long v = paymentVersion(order);
        ok(call(post("/api/v1/cook/orders/" + order + "/payment/problem").content(
                "{\"expectedVersion\":" + v + ",\"kind\":\"NOT_RECEIVED\"}"), as(cookSubject)), 200);
        assertThat(paymentCases(order)).isEqualTo("PAYMENT/HIGH/OPEN");
        // A retry with the same (now stale) version changes nothing and opens nothing.
        assertThat(call(post("/api/v1/cook/orders/" + order + "/payment/problem").content(
                "{\"expectedVersion\":" + v + ",\"kind\":\"NOT_RECEIVED\"}"), as(cookSubject)).getStatus()).isIn(409, 422);
        assertThat(paymentCases(order)).isEqualTo("PAYMENT/HIGH/OPEN");

        // The order ends, staff record a refund obligation: still one open payment case for the order.
        long ov = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
        ok(call(post("/api/v1/cook/orders/" + order + "/reject").header("Idempotency-Key", "rec-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + ov + ",\"reasonCode\":\"INGREDIENT_UNAVAILABLE\"}"), as(cookSubject)), 200);
        ok(call(post("/api/v1/ops/orders/" + order + "/payment/decision").content("{\"expectedVersion\":" + paymentVersion(order)
                + ",\"outcome\":\"REFUND_OWED\",\"reason\":\"kitchen rejected after the transfer report\"}"), logins.loginWithMfa(financeSubject, "Finance")), 200);
        assertThat(paymentCases(order)).as("one open payment case per order").isEqualTo("PAYMENT/HIGH/OPEN");

        String description = jdbc.queryForObject("SELECT description FROM support_case WHERE order_id = ? AND category = 'PAYMENT'", String.class, order);
        assertThat(description).doesNotContain("SYN-REF-1", "synthetic.kitchen@instapay", "13500", "135.00");
        JsonNode mine = ok(call(get("/api/v1/support/cases"), as(customerSubject)), 200);
        assertThat(mine.toString()).contains("PAYMENT");
        String stranger = UUID.randomUUID().toString();
        logins.login(stranger, "Stranger");
        assertThat(ok(call(get("/api/v1/support/cases"), as(stranger)), 200).toString()).doesNotContain("PAYMENT");
        String agent = staff("support.case");
        assertThat(ok(call(get("/api/v1/ops/cases"), logins.login(agent, "Agent")), 200).toString()).contains(order.toString());
    }
}
