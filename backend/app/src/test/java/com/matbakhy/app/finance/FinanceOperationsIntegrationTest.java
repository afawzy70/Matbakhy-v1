package com.matbakhy.app.finance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.finance.internal.LedgerService;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
 * TC-034, TC-035, TC-036 on PostgreSQL: cook-collected cash is never platform revenue, full refunds are bounded by
 * verified collection less committed refunds and reverse commission once, settlement lines are unique, reviewed batches
 * immutable, and payment needs evidence and a second person.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class FinanceOperationsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired LedgerService ledger;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    String verifier;
    String refunderA;
    String refunderB;
    String settlerA;
    String settlerB;
    UUID kitchen;
    UUID meal;
    UUID slot;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        fx.verifyPhone(logins.login(customerSubject, "Customer").getPrincipal().id());
        cookSubject = UUID.randomUUID().toString();
        UUID cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(staff("cook.review"), "Reviewer").getPrincipal().id());
        verifier = staff("finance.verify", "finance.read");
        refunderA = staff("finance.refund_approve");
        refunderB = staff("finance.refund_approve");
        settlerA = staff("finance.settlement", "finance.read");
        settlerB = staff("finance.settlement");
        meal = fx.meal(kitchen, 12000, "كباب", "Kebab");
        slot = fx.slot(kitchen, 300, 50, meal, 40);
    }

    private String staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        return subject;
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, String who) throws Exception {
        return mvc.perform(r.with(authentication(logins.login(who, "User"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
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

    private void cook(UUID order, String action, String extra) throws Exception {
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(order) + extra + "}"), cookSubject), 200);
    }

    /** Pickup order of 2 x 12000 (total 24000, commission 2400) completed, cash reported and verified, commission posted. */
    private UUID settledCashOrder() throws Exception {
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"PICKUP\"}"), customerSubject), 201);
        UUID order = UUID.fromString(ok(call(post("/api/v1/orders").header("Idempotency-Key", key()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), customerSubject), 201)
                .get("id").asString());
        cook(order, "accept", "");
        cook(order, "prepare", "");
        cook(order, "ready", "");
        String code = ok(call(get("/api/v1/orders/" + order), customerSubject), 200).get("handoverCode").asString();
        cook(order, "handover", ",\"code\":\"" + code + "\"");
        JsonNode report = ok(call(post("/api/v1/cook/orders/" + order + "/cash-reports").header("Idempotency-Key", key()).content("""
                {"amountMinor":24000,"currency":"EGP","collectedAt":"%s","method":"CASH","expectedVersion":%d}""".formatted(clock.instant(), version(order))),
                cookSubject), 201);
        ok(call(post("/api/v1/ops/finance/cash-reports/" + report.get("id").asString() + "/verification").header("Idempotency-Key", key()).content(
                "{\"decision\":\"VERIFY\",\"verifiedAmountMinor\":24000,\"reference\":\"bank-slip\",\"expectedVersion\":" + report.get("version").asLong() + "}"),
                verifier), 200);
        UUID event = jdbc.queryForObject("SELECT id FROM order_event WHERE order_id = ? AND to_state = 'COMPLETED'", UUID.class, order);
        ledger.postCommission(event, order);
        return order;
    }

    private UUID commissionGroup(UUID order) {
        return jdbc.queryForObject("SELECT id FROM ledger_group WHERE order_id = ? AND kind = 'COMMISSION'", UUID.class, order);
    }

    private MockHttpServletResponse requestRefund(UUID order, long amount, String who) throws Exception {
        return call(post("/api/v1/ops/finance/refunds").header("Idempotency-Key", key()).content(
                "{\"orderId\":\"" + order + "\",\"caseId\":\"CS-0000ABCD\",\"fullAmountMinor\":" + amount + ",\"reason\":\"food safety complaint\"}"), who);
    }

    private MockHttpServletResponse refundAction(String refundId, String action, String evidence, String who) throws Exception {
        long v = jdbc.queryForObject("SELECT version FROM refund WHERE id = ?::uuid", Long.class, refundId);
        String ev = evidence == null ? "" : ",\"evidenceRef\":\"" + evidence + "\"";
        return call(post("/api/v1/ops/finance/refunds/" + refundId + "/action").header("Idempotency-Key", key())
                .content("{\"action\":\"" + action + "\"" + ev + ",\"reason\":\"checked\",\"expectedVersion\":" + v + "}"), who);
    }

    private JsonNode statement() throws Exception {
        String today = LocalDate.now(ZoneId.of("Africa/Cairo")).toString();
        return ok(call(get("/api/v1/ops/finance/statements").param("kitchenId", kitchen.toString()).param("from", today).param("to", today), settlerA), 200);
    }

    // ---- TC-034 ----

    @Test
    void cook_collected_cash_creates_only_a_commission_receivable() throws Exception {
        UUID order = settledCashOrder();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id WHERE g.order_id = ? AND e.amount_minor = 24000""",
                Integer.class, order)).as("no gross cash receipt in the platform ledger").isZero();
        assertThat(jdbc.queryForObject("""
                SELECT sum(e.amount_minor) FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id
                WHERE g.order_id = ? AND e.account = 'COOK_COMMISSION_RECEIVABLE' AND e.direction = 'DEBIT'""", Long.class, order)).isEqualTo(2400L);
        JsonNode s = statement();
        assertThat(s.get("direction").asString()).isEqualTo("COOK_PAYS_PLATFORM");
        assertThat(s.get("summary").get("cashVerified").get("amountMinor").asLong()).as("cook's cash, shown as position only").isEqualTo(24000);
        assertThat(s.get("summary").get("commissionOutstanding").get("amountMinor").asLong()).isEqualTo(2400);
        assertThat(s.get("unsettledEntries").size()).isEqualTo(1);
        assertThat(call(get("/api/v1/ops/finance/statements").param("kitchenId", kitchen.toString()), customerSubject).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/finance/statements").param("kitchenId", UUID.randomUUID().toString()), settlerA).getStatus()).isEqualTo(404);
    }

    // ---- TC-035 ----

    @Test
    void refunds_are_full_bounded_by_verified_collection_and_reverse_commission_once() throws Exception {
        UUID order = settledCashOrder();
        assertThat(requestRefund(order, 1000, refunderA).getStatus()).as("full refund only").isEqualTo(422);
        assertThat(requestRefund(order, 30000, refunderA).getStatus()).as("over-refund").isEqualTo(422);
        assertThat(requestRefund(order, 24000, settlerA).getStatus()).isEqualTo(403);
        String refund = ok(requestRefund(order, 24000, refunderA), 200).get("id").asString();
        assertThat(requestRefund(order, 24000, refunderB).getStatus()).as("committed refund blocks another").isEqualTo(409);
        assertThat(refundAction(refund, "PAYMENT_RECORDED", "bank-ref-1", refunderB).getStatus()).as("not approved yet").isEqualTo(409);
        assertThat(refundAction(refund, "APPROVE", null, refunderA).getStatus()).as("requester cannot approve").isEqualTo(403);
        JsonNode approved = ok(refundAction(refund, "APPROVE", null, refunderB), 200);
        assertThat(approved.get("state").asString()).isEqualTo("APPROVED");
        JsonNode s = statement();
        assertThat(s.get("refunds").get(0).get("state").asString()).as("approval is not payment").isEqualTo("APPROVED");
        assertThat(s.get("summary").get("commissionReversed").get("amountMinor").asLong()).isZero();
        assertThat(refundAction(refund, "PAYMENT_RECORDED", null, refunderA).getStatus()).as("evidence required").isEqualTo(422);
        ok(refundAction(refund, "PAYMENT_RECORDED", "bank-ref-1", refunderA), 200);
        assertThat(refundAction(refund, "VERIFY", null, refunderA).getStatus()).as("payer cannot verify").isEqualTo(403);
        String verifyKey = key();
        long v = jdbc.queryForObject("SELECT version FROM refund WHERE id = ?::uuid", Long.class, refund);
        String body = "{\"action\":\"VERIFY\",\"reason\":\"bank statement matched\",\"expectedVersion\":" + v + "}";
        JsonNode verified = ok(call(post("/api/v1/ops/finance/refunds/" + refund + "/action").header("Idempotency-Key", verifyKey).content(body), refunderB), 200);
        assertThat(verified.get("state").asString()).isEqualTo("VERIFIED");
        ok(call(post("/api/v1/ops/finance/refunds/" + refund + "/action").header("Idempotency-Key", verifyKey).content(body), refunderB), 200);
        assertThat(refundAction(refund, "VERIFY", null, refunderB).getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM ledger_group WHERE order_id = ? AND kind = 'COMMISSION_REVERSAL'", Integer.class, order))
                .as("reversed once").isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT sum(CASE WHEN e.direction = 'DEBIT' THEN e.amount_minor ELSE -e.amount_minor END) FROM ledger_entry e JOIN ledger_group g ON g.id = e.group_id
                WHERE g.order_id = ? AND e.account = 'COOK_COMMISSION_RECEIVABLE'""", Long.class, order)).as("net receivable").isZero();
        assertThat(statement().get("summary").get("commissionOutstanding").get("amountMinor").asLong()).isZero();
        assertThat(requestRefund(order, 24000, refunderA).getStatus()).as("nothing left to refund").isEqualTo(409);

        UUID other = settledCashOrder();
        String rejected = ok(requestRefund(other, 24000, refunderA), 200).get("id").asString();
        ok(refundAction(rejected, "REJECT", null, refunderB), 200);
        assertThat(requestRefund(other, 24000, refunderA).getStatus()).as("a rejected refund frees the amount").isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE object_id = ?", Integer.class, refund)).isGreaterThanOrEqualTo(4);
    }

    // ---- TC-036 ----

    @Test
    void settlement_lines_are_unique_and_reviewed_batches_immutable() throws Exception {
        UUID first = settledCashOrder();
        UUID second = settledCashOrder();
        UUID g1 = commissionGroup(first);
        UUID g2 = commissionGroup(second);
        String today = LocalDate.now(ZoneId.of("Africa/Cairo")).toString();
        String batchBody = "{\"kitchenId\":\"" + kitchen + "\",\"period\":{\"from\":\"" + today + "\",\"to\":\"" + today + "\"},\"entryIds\":[\"%s\"]}";

        // Concurrent batches over the same entry: exactly one wins.
        List<Integer> statuses = concurrently(
                () -> call(post("/api/v1/ops/finance/settlements").header("Idempotency-Key", key()).content(batchBody.formatted(g2)), settlerA).getStatus(),
                () -> call(post("/api/v1/ops/finance/settlements").header("Idempotency-Key", key()).content(batchBody.formatted(g2)), settlerB).getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        String draftWithG2 = jdbc.queryForObject("SELECT batch_id::text FROM settlement_line WHERE ledger_group_id = ? AND active", String.class, g2);
        long draftVersion = jdbc.queryForObject("SELECT version FROM settlement_batch WHERE id = ?::uuid", Long.class, draftWithG2);
        ok(call(post("/api/v1/ops/finance/settlements/" + draftWithG2 + "/action").header("Idempotency-Key", key())
                .content("{\"action\":\"CANCEL\",\"expectedVersion\":" + draftVersion + "}"), settlerA), 200);

        JsonNode batch = ok(call(post("/api/v1/ops/finance/settlements").header("Idempotency-Key", key()).content(
                "{\"kitchenId\":\"" + kitchen + "\",\"period\":{\"from\":\"" + today + "\",\"to\":\"" + today + "\"},\"entryIds\":[\"" + g1 + "\",\"" + g2 + "\"]}"),
                settlerA), 200);
        assertThat(batch.get("total").get("amountMinor").asLong()).as("released entry reusable after cancel").isEqualTo(4800);
        assertThat(batch.get("direction").asString()).isEqualTo("COOK_PAYS_PLATFORM");
        String id = batch.get("id").asString();
        assertThat(call(post("/api/v1/ops/finance/settlements").header("Idempotency-Key", key()).content(batchBody.formatted(g1)), settlerB).getStatus())
                .as("entry settles once").isEqualTo(409);
        assertThat(action(id, "REVIEW", null, settlerA).getStatus()).as("creator cannot review").isEqualTo(403);
        ok(action(id, "REVIEW", null, settlerB), 200);
        assertThatThrownBy(() -> jdbc.update("UPDATE settlement_line SET amount_minor = 1 WHERE batch_id = ?::uuid", id)).hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM settlement_line WHERE batch_id = ?::uuid", id)).hasMessageContaining("append-only");
        assertThat(action(id, "CANCEL", null, settlerA).getStatus()).as("reviewed batch immutable").isEqualTo(409);
        assertThat(action(id, "PAYMENT_RECORDED", null, settlerA).getStatus()).as("external reference required").isEqualTo(422);
        ok(action(id, "PAYMENT_RECORDED", "instapay-ref-778", settlerA), 200);
        assertThat(action(id, "RECONCILE", null, settlerA).getStatus()).as("recorder cannot reconcile").isEqualTo(403);
        JsonNode reconciled = ok(action(id, "RECONCILE", null, settlerB), 200);
        assertThat(reconciled.get("state").asString()).isEqualTo("RECONCILED");
        JsonNode s = statement();
        assertThat(s.get("summary").get("commissionSettled").get("amountMinor").asLong()).isEqualTo(4800);
        assertThat(s.get("summary").get("commissionOutstanding").get("amountMinor").asLong()).isZero();
        assertThat(s.get("unsettledEntries").size()).isZero();
        assertThat(call(post("/api/v1/ops/finance/settlements").header("Idempotency-Key", key()).content(batchBody.formatted(g1)), refunderA).getStatus())
                .isEqualTo(403);
    }

    private MockHttpServletResponse action(String batch, String action, String evidence, String who) throws Exception {
        long v = jdbc.queryForObject("SELECT version FROM settlement_batch WHERE id = ?::uuid", Long.class, batch);
        String ev = evidence == null ? "" : ",\"evidenceRef\":\"" + evidence + "\"";
        return call(post("/api/v1/ops/finance/settlements/" + batch + "/action").header("Idempotency-Key", key())
                .content("{\"action\":\"" + action + "\"" + ev + ",\"expectedVersion\":" + v + "}"), who);
    }

    private static <T> List<T> concurrently(Callable<T> a, Callable<T> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<T>> f = new ArrayList<>();
            for (Callable<T> c : List.of(a, b)) {
                f.add(pool.submit(() -> {
                    start.await();
                    return c.call();
                }));
            }
            start.countDown();
            return List.of(f.get(0).get(), f.get(1).get());
        } finally {
            pool.shutdownNow();
        }
    }
}
