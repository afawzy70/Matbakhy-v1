package com.matbakhy.app.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import com.matbakhy.finance.internal.LedgerService;
import com.matbakhy.identity.internal.AccountErasureService;
import java.time.Clock;
import java.util.Map;
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
 * MB-039 / TC-055 on real PostgreSQL: a synthetic customer with orders, an address, a review, a support case, a
 * favorite and a device requests deletion. Nothing is erased during the grace period or while an order is active; then
 * every module erases its personal data in one transaction, order amounts and items stay, terminal orders stay
 * immutable for anything else, the old session no longer works, a second run changes nothing, and the audit record holds
 * counts only. A cook account is put on hold for staff review instead.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class AccountErasureIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired AccountErasureService erasure;
    @Autowired LedgerService ledger;

    TestCommerce fx;
    String customerSubject;
    UUID customer;
    String cookSubject;
    UUID cook;
    UUID meal;
    UUID slot;
    UUID address;
    UUID kitchen;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        customer = logins.login(customerSubject, "Erasure Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        UUID zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 12000, "فتة", "Fatta");
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
        return r.getContentAsString().isEmpty() ? null : json.readTree(r.getContentAsString());
    }

    private UUID placeOrder(AccountAuthentication who) throws Exception {
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), who), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", "era-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), who), 201);
        return UUID.fromString(order.get("id").asString());
    }

    private void cancel(UUID order, AccountAuthentication who) throws Exception {
        long v = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
        ok(call(post("/api/v1/orders/" + order + "/cancel").header("Idempotency-Key", "era-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + v + ",\"reasonCode\":\"CHANGED_MIND\"}"), who), 200);
    }

    @Test
    void a_due_request_erases_personal_data_everywhere_keeps_amounts_and_is_audited() throws Exception {
        AccountAuthentication me = as(customerSubject);
        UUID done = placeOrder(me);
        cancel(done, me);
        UUID active = placeOrder(me);
        long totalBefore = jdbc.queryForObject("SELECT total_minor FROM customer_order WHERE id = ?", Long.class, done);
        int itemsBefore = jdbc.queryForObject("SELECT count(*) FROM order_item WHERE order_id = ?", Integer.class, done);
        jdbc.update("INSERT INTO review (id, order_id, customer_id, kitchen_id, rating, text, created_at) "
                + "SELECT ?, id, customer_id, kitchen_id, 4, 'Synthetic review naming my street', now() FROM customer_order WHERE id = ?", UUID.randomUUID(), done);
        ok(call(post("/api/v1/support/cases").content("{\"orderId\":\"" + done + "\",\"category\":\"QUALITY\",\"description\":\"Synthetic complaint text\"}"), me), 201);
        jdbc.update("INSERT INTO favorite (user_id, meal_id, created_at) VALUES (?, ?, now())", customer, meal);
        ok(call(put("/api/v1/me/devices/install-erasure-1").content(
                "{\"platform\":\"ANDROID\",\"pushToken\":\"synthetic-push-token-0123456789\",\"environment\":\"DEVELOPMENT\",\"appVersion\":\"0.1.0\"}"), me), 200);
        ok(call(post("/api/v1/me/deletion-requests").content("{\"confirm\":true,\"policyVersion\":\"retention-policy-draft\"}"), me), 200);
        assertThat(call(get("/api/v1/me"), as(customerSubject)).getStatus()).as("disabled at once on request").isEqualTo(403);

        assertThat(erasure.eraseDue().erased()).as("nothing during the grace period").isZero();
        jdbc.update("UPDATE deletion_request SET requested_at = now() - interval '15 days' WHERE user_id = ?", customer);
        assertThat(erasure.eraseDue().waiting()).as("an active order makes it wait").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT display_name FROM app_user WHERE id = ?", String.class, customer)).isNotNull();

        jdbc.update("UPDATE customer_order SET state = 'CANCELLED', terminal_at = now(), version = version + 1 WHERE id = ?", active);
        assertThat(erasure.eraseDue().erased()).isEqualTo(1);

        Map<String, Object> account = jdbc.queryForMap("SELECT subject, display_name, phone_e164, status FROM app_user WHERE id = ?", customer);
        assertThat(account.get("display_name")).isNull();
        assertThat(account.get("phone_e164")).isNull();
        assertThat(account.get("status")).isEqualTo("DELETED");
        assertThat((String) account.get("subject")).startsWith("erased:");
        Map<String, Object> addr = jdbc.queryForMap("SELECT recipient_name, phone_e164, street, erased_at FROM address WHERE id = ?", address);
        assertThat(addr.get("recipient_name")).isNull();
        assertThat(addr.get("phone_e164")).isNull();
        assertThat(addr.get("street")).isNull();
        assertThat(addr.get("erased_at")).isNotNull();
        for (UUID o : new UUID[] {done, active}) {
            Map<String, Object> row = jdbc.queryForMap("SELECT contact_snapshot::text AS c, address_snapshot::text AS a, personal_data_erased_at FROM customer_order WHERE id = ?", o);
            assertThat(row.get("c")).isEqualTo("{\"erased\": true}");
            assertThat(row.get("a")).isEqualTo("{\"erased\": true}");
            assertThat(row.get("personal_data_erased_at")).isNotNull();
        }
        assertThat(jdbc.queryForObject("SELECT total_minor FROM customer_order WHERE id = ?", Long.class, done)).as("amounts kept").isEqualTo(totalBefore);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_item WHERE order_id = ?", Integer.class, done)).isEqualTo(itemsBefore);
        assertThat(jdbc.queryForMap("SELECT text, rating FROM review WHERE order_id = ?", done)).containsEntry("text", null).containsEntry("rating", 4);
        assertThat(jdbc.queryForObject("SELECT description FROM support_case WHERE customer_id = ?", String.class, customer)).isEqualTo("[erased on account deletion]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM favorite WHERE user_id = ?", Integer.class, customer)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_registration WHERE user_id = ?", Integer.class, customer)).isZero();
        assertThat(jdbc.queryForObject("SELECT state FROM deletion_request WHERE user_id = ?", String.class, customer)).isEqualTo("COMPLETED");

        String auditDetails = jdbc.queryForObject(
                "SELECT details::text FROM audit_event WHERE action = 'account.erasure' AND object_id = ? AND outcome = 'ALLOWED'", String.class, customer.toString());
        assertThat(auditDetails).contains("orders.orderSnapshots").doesNotContain("Erasure Customer").doesNotContain("+20").doesNotContain("street");

        assertThatThrownBy(() -> jdbc.update("UPDATE customer_order SET total_minor = total_minor + 1 WHERE id = ?", done))
                .as("terminal orders stay immutable apart from the one erasure").hasMessageContaining("immutable");
        assertThat(erasure.eraseDue().erased()).as("idempotent").isZero();
        // The identity link is gone: signing in again with the same provider identity creates a new, empty account.
        AccountAuthentication again = as(customerSubject);
        assertThat(again.getPrincipal().id()).isNotEqualTo(customer);
        assertThat(json.readTree(ok(call(get("/api/v1/orders"), again), 200).toString()).get("items").size()).as("no access to erased history").isZero();
    }

    /**
     * Found 2026-10-13: a customer's own support reply and the transfer reference typed for a deposit (D16) live in
     * append-only tables. Erasure must still remove them (and only them): before the fix the reply made the whole erasure
     * fail on the append-only trigger, and the reference was never erased.
     */
    @Test
    void erasure_removes_the_customers_support_replies_and_transfer_references_but_no_one_elses_history() throws Exception {
        AccountAuthentication me = as(customerSubject);
        jdbc.update("""
                INSERT INTO kitchen_payment_profile (kitchen_id, deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier, recipient_name,
                                                     recipient_status, approved_by, approved_at)
                VALUES (?, 5000, 100000, 'INSTAPAY', 'kitchen.erasure@instapay', 'Synthetic Kitchen', 'APPROVED', ?, now())""", kitchen, cook);
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\",\"paymentMethod\":\"DEPOSIT\"}"), me), 201);
        UUID order = UUID.fromString(ok(call(post("/api/v1/orders").header("Idempotency-Key", "era-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), me), 201).get("id").asString());
        ok(call(post("/api/v1/orders/" + order + "/payment/transfer-report").content("{\"reference\":\"ERASE-ME-REF-77\"}"), me), 200);
        long pv = jdbc.queryForObject("SELECT version FROM order_payment WHERE order_id = ?", Long.class, order);
        ok(call(post("/api/v1/cook/orders/" + order + "/payment/receipt").content("{\"expectedVersion\":" + pv + ",\"amountMinor\":"
                + jdbc.queryForObject("SELECT deposit_minor FROM order_payment WHERE order_id = ?", Long.class, order)
                + ",\"reference\":\"KITCHEN-REF-1\"}"), as(cookSubject)), 200);
        cancel(order, me);
        UUID caseId = UUID.fromString(ok(call(post("/api/v1/support/cases").content("{\"orderId\":\"" + order
                + "\",\"category\":\"QUALITY\",\"description\":\"Synthetic complaint text\"}"), me), 201).get("id").asString());
        ok(call(post("/api/v1/support/cases/" + caseId + "/messages").header("Idempotency-Key", "era-" + UUID.randomUUID())
                .content("{\"body\":\"My phone is 01000000000, call me\"}"), me), 201);

        ok(call(post("/api/v1/me/deletion-requests").content("{\"confirm\":true,\"policyVersion\":\"retention-policy-draft\"}"), me), 200);
        jdbc.update("UPDATE deletion_request SET requested_at = now() - interval '15 days' WHERE user_id = ?", customer);
        assertThat(erasure.eraseDue().erased()).as("a customer with a support reply and a deposit can be erased").isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT body FROM case_message WHERE case_id = ? AND author_role = 'CUSTOMER'", String.class, caseId))
                .isNotEmpty().containsOnly("[erased on account deletion]");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE order_id = ? AND actor_type = 'CUSTOMER' AND reference IS NOT NULL",
                Integer.class, order)).as("the customer's transfer reference is erased").isZero();
        assertThat(jdbc.queryForObject("SELECT reference FROM payment_event WHERE order_id = ? AND actor_type = 'COOK' AND reference IS NOT NULL",
                String.class, order)).as("the kitchen's own history stays").isEqualTo("KITCHEN-REF-1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE order_id = ?", Integer.class, order))
                .as("no payment event is removed").isGreaterThanOrEqualTo(3);
        // The append-only protection still holds for everything except the erasure itself.
        assertThatThrownBy(() -> jdbc.update("UPDATE case_message SET body = 'rewritten' WHERE case_id = ?", caseId)).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM case_message WHERE case_id = ?", caseId)).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_event SET amount_minor = 1 WHERE order_id = ?", order)).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("UPDATE payment_event SET reference = NULL WHERE order_id = ? AND actor_type = 'COOK'", order))
                .as("only a customer's reference may be erased").hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM payment_event WHERE order_id = ?", order)).hasMessageContaining("append-only");
    }

    private void cookAction(UUID order, String action, String extra) throws Exception {
        long v = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", "era-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + v + extra + "}"), as(cookSubject)), 200);
    }

    /** TC-055 "balanced finance remains": a completed order's ledger (synthetic 10% commission fixture) is untouched by erasure. */
    @Test
    void erasure_keeps_the_ledger_of_a_completed_order_balanced_and_unchanged() throws Exception {
        AccountAuthentication me = as(customerSubject);
        UUID order = placeOrder(me);
        cookAction(order, "accept", "");
        cookAction(order, "prepare", "");
        cookAction(order, "ready", "");
        cookAction(order, "dispatch", ",\"deliveryReference\":\"Synthetic rider\"");
        cookAction(order, "handover", ",\"code\":\"" + ok(call(get("/api/v1/orders/" + order), me), 200).get("handoverCode").asString() + "\"");
        UUID completed = jdbc.queryForObject("SELECT id FROM order_event WHERE order_id = ? AND to_state = 'COMPLETED'", UUID.class, order);
        ledger.postCommission(completed, order);
        String ledgerSql = """
                SELECT string_agg(e.account || ':' || e.direction || ':' || e.amount_minor, ',' ORDER BY e.account, e.direction)
                FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id WHERE g.order_id = ?""";
        String before = jdbc.queryForObject(ledgerSql, String.class, order);
        assertThat(before).as("a commission group was posted").isNotNull();

        ok(call(post("/api/v1/me/deletion-requests").content("{\"confirm\":true,\"policyVersion\":\"retention-policy-draft\"}"), me), 200);
        jdbc.update("UPDATE deletion_request SET requested_at = now() - interval '15 days' WHERE user_id = ?", customer);
        assertThat(erasure.eraseDue().erased()).isEqualTo(1);

        assertThat(jdbc.queryForObject(ledgerSql, String.class, order)).as("ledger entries unchanged").isEqualTo(before);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ledger_group g WHERE g.order_id = ? AND
                  (SELECT coalesce(sum(CASE WHEN e.direction = 'DEBIT' THEN e.amount_minor ELSE -e.amount_minor END), 0)
                   FROM ledger_entry e WHERE e.group_id = g.id) <> 0""", Integer.class, order)).as("every group still balances").isZero();
        assertThat(jdbc.queryForObject("SELECT state || '|' || total_minor FROM customer_order WHERE id = ?", String.class, order))
                .startsWith("COMPLETED|");
        assertThat(jdbc.queryForObject("SELECT contact_snapshot::text FROM customer_order WHERE id = ?", String.class, order)).isEqualTo("{\"erased\": true}");
    }

    @Test
    void a_cook_account_is_put_on_hold_for_staff_review_and_keeps_its_data() throws Exception {
        AccountAuthentication cookAuth = as(cookSubject);
        ok(call(post("/api/v1/me/deletion-requests").content("{\"confirm\":true,\"policyVersion\":\"retention-policy-draft\"}"), cookAuth), 200);
        jdbc.update("UPDATE deletion_request SET requested_at = now() - interval '15 days' WHERE user_id = ?", cook);
        assertThat(erasure.eraseDue().onHold()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT state, legal_hold_reason FROM deletion_request WHERE user_id = ?", cook))
                .containsEntry("state", "ON_HOLD").containsEntry("legal_hold_reason", "COOK_ACCOUNT_REVIEW");
        assertThat(jdbc.queryForObject("SELECT display_name FROM app_user WHERE id = ?", String.class, cook)).isNotNull();
    }
}
