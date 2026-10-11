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
import com.matbakhy.finance.internal.LedgerPostingJob;
import com.matbakhy.finance.internal.LedgerService;
import java.time.Clock;
import java.time.Instant;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TC-032, TC-033, TC-120 on PostgreSQL: completion does not imply payment, cook reports and finance verification are
 * separate (with separation of duties), short reports are exceptions, and commission posts once per completion event
 * as a balanced, immutable group from the placement snapshot.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class CashAndLedgerIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired LedgerService ledger;
    @Autowired PlatformTransactionManager tx;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    UUID customer;
    UUID cook;
    UUID kitchen;
    UUID zone;
    UUID meal;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 12000, "كفتة", "Kofta");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(customer, zone);
    }

    // ---- helpers ----

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
        return "fin-" + UUID.randomUUID();
    }

    private long version(UUID order) {
        return jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
    }

    private void cookCommand(UUID order, String action, String extra) throws Exception {
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(order) + extra + "}"), as(cookSubject)), 200);
    }

    /** A delivery order of two portions at 12000 with a 3000 fee (total 27000), taken to COMPLETED with the real code. */
    private UUID completedOrder() throws Exception {
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), as(customerSubject)), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", key()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), as(customerSubject)), 201);
        UUID id = UUID.fromString(order.get("id").asString());
        cookCommand(id, "accept", "");
        cookCommand(id, "prepare", "");
        cookCommand(id, "ready", "");
        cookCommand(id, "dispatch", ",\"deliveryReference\":\"Synthetic rider\"");
        String code = ok(call(get("/api/v1/orders/" + id), as(customerSubject)), 200).get("handoverCode").asString();
        cookCommand(id, "handover", ",\"code\":\"" + code + "\"");
        return id;
    }

    private UUID completionEvent(UUID order) {
        return jdbc.queryForObject("SELECT id FROM order_event WHERE order_id = ? AND to_state = 'COMPLETED'", UUID.class, order);
    }

    private String staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        return subject;
    }

    private MockHttpServletResponse report(UUID order, long amount) throws Exception {
        return call(post("/api/v1/cook/orders/" + order + "/cash-reports").header("Idempotency-Key", key()).content("""
                {"amountMinor":%d,"currency":"EGP","collectedAt":"%s","method":"CASH","externalReference":"receipt-1","expectedVersion":%d}"""
                .formatted(amount, clock.instant(), version(order))), as(cookSubject));
    }

    private MockHttpServletResponse verify(String staffSubject, UUID reportId, String decision, long amount, String reason, long version) throws Exception {
        String reasonJson = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        return call(post("/api/v1/ops/finance/cash-reports/" + reportId + "/verification").header("Idempotency-Key", key()).content(
                "{\"decision\":\"" + decision + "\",\"verifiedAmountMinor\":" + amount + reasonJson + ",\"reference\":\"bank-slip-9\",\"expectedVersion\":"
                        + version + "}"), as(staffSubject));
    }

    private String cashState(UUID order) {
        return jdbc.queryForObject("SELECT state FROM cash_obligation WHERE order_id = ?", String.class, order);
    }

    // ---- TC-032: completion does not imply payment ----

    @Test
    void a_completed_order_without_a_report_stays_due_with_no_invented_receipt() throws Exception {
        UUID order = completedOrder();
        assertThat(ok(call(get("/api/v1/orders/" + order), as(customerSubject)), 200).get("cashState").asString()).isEqualTo("DUE");
        String finance = staff("finance.read");
        JsonNode list = ok(call(get("/api/v1/ops/finance/cash-reports").param("kitchenId", kitchen.toString()), as(finance)), 200);
        assertThat(list.get("items").size()).isEqualTo(1);
        JsonNode item = list.get("items").get(0);
        assertThat(item.get("orderState").asString()).isEqualTo("COMPLETED");
        assertThat(item.get("cashState").asString()).isEqualTo("DUE");
        assertThat(item.get("amountDue").get("amountMinor").asLong()).isEqualTo(27000);
        assertThat(item.get("report").isNull()).as("no invented receipt").isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cash_report WHERE order_id = ?", Integer.class, order)).isZero();
        assertThat(call(get("/api/v1/ops/finance/cash-reports"), as(customerSubject)).getStatus()).isEqualTo(403);
    }

    // ---- TC-033: commission once, balanced, from the snapshot ----

    @Test
    void commission_posts_once_per_completion_event_as_a_balanced_group_from_the_snapshot() throws Exception {
        UUID order = completedOrder();
        UUID event = completionEvent(order);
        fx.activatePolicy(0);
        jdbc.update("UPDATE commerce_policy SET status = 'RETIRED' WHERE status = 'ACTIVE'");
        assertThat(ledger.postCommission(event, order)).isTrue();
        assertThat(ledger.postCommission(event, order)).as("duplicate delivery of the completion event").isFalse();
        assertThat(new LedgerPostingJob(ledger).postPending()).as("job finds nothing left for this order").isGreaterThanOrEqualTo(0);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_group WHERE order_id = ?", Integer.class, order)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT base_minor FROM ledger_group WHERE order_id = ?", Long.class, order))
                .as("delivery excluded").isEqualTo(24000L);
        assertThat(jdbc.queryForObject("SELECT rate_bps FROM ledger_group WHERE order_id = ?", Integer.class, order)).isEqualTo(1000);
        assertThat(jdbc.queryForObject("""
                SELECT sum(amount_minor) FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id
                WHERE g.order_id = ? AND e.direction = 'DEBIT' AND e.account = 'COOK_COMMISSION_RECEIVABLE'""", Long.class, order)).isEqualTo(2400L);
        assertThat(jdbc.queryForObject("""
                SELECT sum(amount_minor) FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id
                WHERE g.order_id = ? AND e.direction = 'CREDIT' AND e.account = 'PLATFORM_COMMISSION_REVENUE'""", Long.class, order)).isEqualTo(2400L);
        assertThat(cashState(order)).as("posting commission records no cash").isEqualTo("DUE");

        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entry SET amount_minor = 1 WHERE group_id = (SELECT id FROM ledger_group WHERE order_id = ?)",
                order)).hasMessageContaining("append-only");
        UUID groupId = jdbc.queryForObject("SELECT id FROM ledger_group WHERE order_id = ?", UUID.class, order);
        assertThatThrownBy(() -> new TransactionTemplate(tx).executeWithoutResult(s -> jdbc.update(
                "INSERT INTO ledger_entry (id, group_id, account, direction, amount_minor) VALUES (?, ?, 'COOK_COMMISSION_RECEIVABLE', 'DEBIT', 5)",
                UUID.randomUUID(), groupId))).as("rejected at commit by the deferred balance check")
                .rootCause().hasMessageContaining("unbalanced");
        fx.activatePolicy(0);
    }

    @Test
    void the_posting_job_posts_every_completion_once() throws Exception {
        UUID first = completedOrder();
        UUID second = completedOrder();
        LedgerPostingJob job = new LedgerPostingJob(ledger);
        job.postPending();
        job.postPending();
        for (UUID o : new UUID[] {first, second}) {
            assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_group WHERE order_id = ?", Integer.class, o)).isEqualTo(1);
        }
    }

    // ---- TC-120: report and verification are separate ----

    @Test
    void exact_report_then_independent_verification() throws Exception {
        UUID order = completedOrder();
        JsonNode report = ok(report(order, 27000), 201);
        assertThat(report.get("state").asString()).isEqualTo("REPORTED");
        assertThat(report.get("discrepancy").get("amountMinor").asLong()).isZero();
        assertThat(cashState(order)).isEqualTo("REPORTED");
        assertThat(ok(call(get("/api/v1/orders/" + order), as(customerSubject)), 200).get("cashState").asString()).isEqualTo("REPORTED");
        assertThat(report(order, 27000).getStatus()).as("one report per order").isEqualTo(409);

        UUID reportId = UUID.fromString(report.get("id").asString());
        String reader = staff("finance.read");
        assertThat(verify(reader, reportId, "VERIFY", 27000, null, 0).getStatus()).as("read-only finance cannot verify").isEqualTo(403);
        String verifier = staff("finance.verify");
        JsonNode verified = ok(verify(verifier, reportId, "VERIFY", 27000, null, 0), 200);
        assertThat(verified.get("state").asString()).isEqualTo("VERIFIED");
        assertThat(cashState(order)).isEqualTo("VERIFIED");
        assertThat(verify(verifier, reportId, "VERIFY", 27000, null, 1).getStatus()).as("decided once").isEqualTo(409);
    }

    @Test
    void short_or_zero_reports_are_exceptions_and_need_a_reasoned_decision() throws Exception {
        UUID order = completedOrder();
        JsonNode report = ok(report(order, 20000), 201);
        assertThat(report.get("discrepancy").get("amountMinor").asLong()).isEqualTo(-7000);
        assertThat(cashState(order)).as("a short report is not a success").isEqualTo("DISPUTED");
        UUID reportId = UUID.fromString(report.get("id").asString());
        String verifier = staff("finance.verify");
        assertThat(verify(verifier, reportId, "VERIFY", 20000, "accept partial", 0).getContentAsString()).contains("MUST_EQUAL_AMOUNT_DUE_OR_DISPUTE");
        assertThat(verify(verifier, reportId, "DISPUTE", 20000, null, 0).getContentAsString()).contains("REQUIRED_FOR_EXCEPTIONS");
        JsonNode disputed = ok(verify(verifier, reportId, "DISPUTE", 20000, "Cook collected 20000 of 27000; follow-up case", 0), 200);
        assertThat(disputed.get("state").asString()).isEqualTo("DISPUTED");
        assertThat(cashState(order)).isEqualTo("DISPUTED");

        UUID zero = completedOrder();
        ok(report(zero, 0), 201);
        assertThat(cashState(zero)).isEqualTo("DISPUTED");
    }

    @Test
    void reports_need_a_completed_own_order_and_reporters_cannot_verify_themselves() throws Exception {
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"PICKUP\"}"), as(customerSubject)), 201);
        UUID pending = UUID.fromString(ok(call(post("/api/v1/orders").header("Idempotency-Key", key()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), as(customerSubject)), 201)
                .get("id").asString());
        assertThat(report(pending, 12000).getStatus()).as("not completed").isEqualTo(409);

        UUID order = completedOrder();
        String otherCook = UUID.randomUUID().toString();
        TestKitchens.approvedKitchen(jdbc, logins.login(otherCook, "Cook").getPrincipal().id(), customer);
        assertThat(call(post("/api/v1/cook/orders/" + order + "/cash-reports").header("Idempotency-Key", key()).content("""
                {"amountMinor":27000,"currency":"EGP","collectedAt":"%s","method":"CASH","expectedVersion":%d}"""
                .formatted(clock.instant(), version(order))), as(otherCook)).getStatus()).isEqualTo(404);
        assertThat(call(post("/api/v1/cook/orders/" + order + "/cash-reports").header("Idempotency-Key", key()).content("""
                {"amountMinor":27000,"currency":"EGP","collectedAt":"%s","method":"CASH","expectedVersion":%d}"""
                .formatted(Instant.now().plusSeconds(3600), version(order))), as(cookSubject)).getStatus()).as("collected in the future").isEqualTo(422);
        assertThat(call(post("/api/v1/cook/orders/" + order + "/cash-reports").header("Idempotency-Key", key()).content("""
                {"amountMinor":27000,"currency":"EGP","collectedAt":"%s","method":"CARD","expectedVersion":%d}"""
                .formatted(clock.instant(), version(order))), as(cookSubject)).getStatus()).isEqualTo(422);

        UUID reportId = UUID.fromString(ok(report(order, 27000), 201).get("id").asString());
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'finance.verify', 'test grant')", UUID.randomUUID(), cook);
        MockHttpServletResponse self = verify(cookSubject, reportId, "VERIFY", 27000, null, 0);
        assertThat(self.getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND action = 'cash.verify' AND outcome = 'DENIED'",
                Integer.class, cook)).isEqualTo(1);
        assertThat(cashState(order)).isEqualTo("REPORTED");
    }
}
