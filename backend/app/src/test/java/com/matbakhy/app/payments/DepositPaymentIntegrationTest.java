package com.matbakhy.app.payments;

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
import com.matbakhy.orders.internal.OrderLifecycleService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
 * MB-051 (owner decision D16): eligible cash on delivery and manual deposits paid directly to the kitchen's approved
 * recipient. Synthetic data only; no money moves. TC-158: deposit terms are quoted and snapshotted immutably; TC-159: a
 * customer's transfer report is not payment and no preparation happens before the kitchen reports receipt; TC-160:
 * expiry after a reported transfer goes to reconciliation instead of closing silently.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({TestLogins.class, DepositPaymentIntegrationTest.Beans.class})
class DepositPaymentIntegrationTest {

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
    @Autowired OrderLifecycleService lifecycle;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    UUID customer;
    UUID kitchen;
    UUID meal;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).reset();
        fx = new TestCommerce(jdbc, clock);
        fx.activateLaunchPolicy(0);
        customerSubject = UUID.randomUUID().toString();
        customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        UUID cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, cook, reviewer);
        UUID zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 12000, "كفتة", "Kofta");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(customer, zone);
        // Synthetic payment terms: 50% deposit to an approved InstaPay recipient; COD only up to EGP 200.00.
        jdbc.update("""
                INSERT INTO kitchen_payment_profile (kitchen_id, deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier,
                                                     recipient_name, recipient_status, approved_by, approved_at)
                VALUES (?, 5000, 20000, 'INSTAPAY', 'synthetic.kitchen@instapay', 'Synthetic Kitchen', 'APPROVED', ?, now())""", kitchen, reviewer);
    }

    @AfterEach
    void restore() {
        fx.activatePolicy(0);
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

    private MockHttpServletResponse quote(int quantity, String method) throws Exception {
        return call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":" + quantity + "}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\",\"paymentMethod\":\"" + method + "\"}"), as(customerSubject));
    }

    private MockHttpServletResponse place(JsonNode quote) throws Exception {
        return call(post("/api/v1/orders").header("Idempotency-Key", "dep-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), as(customerSubject));
    }

    private long version(UUID order) {
        return jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
    }

    private MockHttpServletResponse cook(UUID order, String action) throws Exception {
        return call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", "dep-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + version(order) + "}"), as(cookSubject));
    }

    private MockHttpServletResponse report(UUID order, String subject) throws Exception {
        return call(post("/api/v1/orders/" + order + "/payment/transfer-report").content("{\"reference\":\"IPN-SYN-001\"}"), as(subject));
    }

    private MockHttpServletResponse receipt(UUID order, long amount, String subject) throws Exception {
        long v = jdbc.queryForObject("SELECT version FROM order_payment WHERE order_id = ?", Long.class, order);
        return call(post("/api/v1/cook/orders/" + order + "/payment/receipt").content("{\"expectedVersion\":" + v + ",\"amountMinor\":" + amount
                + ",\"reference\":\"IPN-SYN-001\"}"), as(subject));
    }

    private UUID depositOrder() throws Exception {
        JsonNode order = ok(place(ok(quote(2, "DEPOSIT"), 201)), 201);
        return UUID.fromString(order.get("id").asString());
    }

    private String paymentState(UUID order) {
        return jdbc.queryForObject("SELECT state FROM order_payment WHERE order_id = ?", String.class, order);
    }

    @Test
    void deposit_terms_are_quoted_and_snapshotted_and_cod_follows_the_kitchen_limit() throws Exception {
        JsonNode q = ok(quote(2, "DEPOSIT"), 201);
        assertThat(q.get("total").get("amountMinor").asLong()).isEqualTo(27000);
        JsonNode pay = q.get("payment");
        assertThat(pay.get("method").asString()).isEqualTo("DEPOSIT");
        assertThat(pay.get("deposit").get("amountMinor").asLong()).as("50% of 27000").isEqualTo(13500);
        assertThat(pay.get("remainder").get("amountMinor").asLong()).isEqualTo(13500);
        assertThat(pay.get("recipient").get("method").asString()).isEqualTo("INSTAPAY");
        assertThat(pay.get("recipient").get("identifier").asString()).isEqualTo("synthetic.kitchen@instapay");

        MockHttpServletResponse cod = quote(2, "COD");
        assertThat(cod.getStatus()).as(cod.getContentAsString()).isEqualTo(422);
        assertThat(cod.getContentAsString()).contains("COD_NOT_ELIGIBLE");
        JsonNode smallCod = ok(quote(1, "COD"), 201);
        assertThat(smallCod.get("payment").get("method").asString()).isEqualTo("COD");
        assertThat(smallCod.get("payment").get("deposit").get("amountMinor").asLong()).isZero();

        JsonNode order = ok(place(q), 201);
        UUID id = UUID.fromString(order.get("id").asString());
        assertThat(order.get("payment").get("state").asString()).isEqualTo("WAITING_FOR_DEPOSIT");
        assertThat(order.get("payment").get("deposit").get("amountMinor").asLong()).isEqualTo(13500);
        assertThat(order.get("payment").get("recipient").get("identifier").asString()).isEqualTo("synthetic.kitchen@instapay");
        assertThat(jdbc.queryForObject("SELECT amount_minor FROM cash_obligation WHERE order_id = ?", Long.class, id))
                .as("cash due at hand-over is the remainder").isEqualTo(13500);

        // The kitchen later changes its terms: the placed order keeps its snapshot, and the terms cannot be edited.
        jdbc.update("UPDATE kitchen_payment_profile SET deposit_bps = 3000, recipient_identifier = 'other@instapay' WHERE kitchen_id = ?", kitchen);
        JsonNode again = ok(call(get("/api/v1/orders/" + id), as(customerSubject)), 200);
        assertThat(again.get("payment").get("deposit").get("amountMinor").asLong()).isEqualTo(13500);
        assertThat(again.get("payment").get("recipient").get("identifier").asString()).isEqualTo("synthetic.kitchen@instapay");
        assertThatThrownBy(() -> jdbc.update("UPDATE order_payment SET deposit_minor = 1 WHERE order_id = ?", id)).hasMessageContaining("immutable");

        // A quote made before the change is refused at placement and must be re-quoted (D13 protocol).
        JsonNode stale = ok(quote(1, "DEPOSIT"), 201);
        jdbc.update("UPDATE kitchen_payment_profile SET deposit_bps = 4000 WHERE kitchen_id = ?", kitchen);
        MockHttpServletResponse refused = place(stale);
        assertThat(refused.getStatus()).as(refused.getContentAsString()).isEqualTo(409);
        assertThat(refused.getContentAsString()).contains("PRICE_CHANGED", "PAYMENT_CHANGED");
    }

    @Test
    void a_transfer_report_is_not_payment_and_preparation_waits_for_the_kitchen_report() throws Exception {
        UUID order = depositOrder();
        // Two concurrent reports from the customer record one transition.
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            results.add(pool.submit(() -> {
                start.await();
                return report(order, customerSubject).getStatus();
            }));
        }
        start.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(paymentState(order)).isEqualTo("CUSTOMER_TRANSFER_REPORTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE order_id = ? AND to_state = 'CUSTOMER_TRANSFER_REPORTED'",
                Integer.class, order)).isEqualTo(1);
        assertThat(report(order, UUID.randomUUID().toString()).getStatus()).as("another customer").isEqualTo(404);

        ok(cook(order, "accept"), 200);
        MockHttpServletResponse early = cook(order, "prepare");
        assertThat(early.getStatus()).as("no preparation on the customer's word").isEqualTo(409);
        assertThat(early.getContentAsString()).contains("DEPOSIT_NOT_RECEIVED");

        assertThat(receipt(order, 13500, UUID.randomUUID().toString()).getStatus()).as("another cook").isIn(403, 404);
        MockHttpServletResponse wrong = receipt(order, 10000, cookSubject);
        assertThat(wrong.getStatus()).as("amount must match the deposit; mismatches are MB-052").isEqualTo(422);
        JsonNode received = ok(receipt(order, 13500, cookSubject), 200);
        assertThat(received.get("state").asString()).isEqualTo("KITCHEN_RECEIPT_REPORTED");
        assertThat(received.toString()).doesNotContainIgnoringCase("verified");
        ok(cook(order, "prepare"), 200);
        assertThat(jdbc.queryForObject("""
                SELECT string_agg(to_state || ':' || actor_type, ',' ORDER BY occurred_at) FROM payment_event WHERE order_id = ?""", String.class, order))
                .isEqualTo("WAITING_FOR_DEPOSIT:CUSTOMER,CUSTOMER_TRANSFER_REPORTED:CUSTOMER,KITCHEN_RECEIPT_REPORTED:COOK");
    }

    /** QA-004: a second, fully approved kitchen owner cannot act on another kitchen's payment; nothing is written. */
    @Test
    void another_approved_kitchen_cannot_receipt_report_a_problem_or_refund_a_foreign_deposit() throws Exception {
        UUID order = depositOrder();
        ok(report(order, customerSubject), 200);
        ok(cook(order, "accept"), 200);
        String otherCookSubject = UUID.randomUUID().toString();
        UUID otherCook = logins.login(otherCookSubject, "Other Cook").getPrincipal().id();
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        UUID otherKitchen = TestKitchens.approvedKitchen(jdbc, otherCook, reviewer);
        assertThat(otherKitchen).isNotEqualTo(kitchen);

        long paymentVersion = jdbc.queryForObject("SELECT version FROM order_payment WHERE order_id = ?", Long.class, order);
        int events = jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE order_id = ?", Integer.class, order);
        String state = paymentState(order);

        assertThat(receipt(order, 13500, otherCookSubject).getStatus()).as("receipt by a foreign kitchen").isIn(403, 404);
        assertThat(call(post("/api/v1/cook/orders/" + order + "/payment/problem")
                .content("{\"expectedVersion\":" + paymentVersion + ",\"kind\":\"NOT_RECEIVED\"}"), as(otherCookSubject)).getStatus())
                .as("problem by a foreign kitchen").isIn(403, 404);
        assertThat(call(post("/api/v1/cook/orders/" + order + "/payment/refund")
                .content("{\"expectedVersion\":" + paymentVersion + ",\"reference\":\"RF-SYN-1\"}"), as(otherCookSubject)).getStatus())
                .as("refund by a foreign kitchen").isIn(403, 404);

        assertThat(jdbc.queryForObject("SELECT version FROM order_payment WHERE order_id = ?", Long.class, order)).isEqualTo(paymentVersion);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM payment_event WHERE order_id = ?", Integer.class, order)).isEqualTo(events);
        assertThat(paymentState(order)).isEqualTo(state);
        // The owning kitchen is unaffected and still succeeds with the same version.
        ok(receipt(order, 13500, cookSubject), 200);
    }

    @Test
    void expiry_after_a_reported_transfer_goes_to_reconciliation_and_without_one_the_deposit_expires() throws Exception {
        UUID reported = depositOrder();
        ok(report(reported, customerSubject), 200);
        UUID silent = depositOrder();
        Instant acceptBy = jdbc.queryForObject("SELECT accept_by FROM customer_order WHERE id = ?", Timestamp.class, reported).toInstant();
        ((MutableClock) clock).advance(Duration.between(clock.instant(), acceptBy.plusSeconds(5)));
        assertThat(lifecycle.expireOne(reported)).isTrue();
        assertThat(lifecycle.expireOne(silent)).isTrue();
        assertThat(paymentState(reported)).isEqualTo("RECONCILE_PENDING");
        assertThat(paymentState(silent)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT state FROM customer_order WHERE id = ?", String.class, reported)).isEqualTo("EXPIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_allocation WHERE order_id = ? AND state = 'RELEASED'", Integer.class, reported))
                .as("capacity released once").isEqualTo(1);
    }

    /** QA-003: a customer's transfer report racing the expiry job is never lost and capacity is released exactly once. */
    @Test
    void a_transfer_report_racing_expiry_is_reconciled_or_refused_never_lost() throws Exception {
        for (int round = 0; round < 6; round++) {
            UUID order = depositOrder();
            Instant acceptBy = jdbc.queryForObject("SELECT accept_by FROM customer_order WHERE id = ?", Timestamp.class, order).toInstant();
            ((MutableClock) clock).advance(Duration.between(clock.instant(), acceptBy.plusSeconds(5)));
            ExecutorService pool = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            Future<Integer> reported = pool.submit(() -> {
                start.await();
                return report(order, customerSubject).getStatus();
            });
            Future<Boolean> expired = pool.submit(() -> {
                start.await();
                return lifecycle.expireOne(order);
            });
            start.countDown();
            int reportStatus = reported.get(60, java.util.concurrent.TimeUnit.SECONDS);
            expired.get(60, java.util.concurrent.TimeUnit.SECONDS);
            pool.shutdown();

            assertThat(jdbc.queryForObject("SELECT state FROM customer_order WHERE id = ?", String.class, order)).isEqualTo("EXPIRED");
            if (reportStatus == 200) {
                assertThat(paymentState(order)).as("round %d: a reported transfer must be reconciled, never silently expired", round)
                        .isEqualTo("RECONCILE_PENDING");
            } else {
                assertThat(reportStatus).as("round %d: a refused report is a state conflict", round).isEqualTo(409);
                assertThat(paymentState(order)).isEqualTo("EXPIRED");
            }
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_allocation WHERE order_id = ? AND state = 'RELEASED'", Integer.class, order))
                    .as("round %d: capacity released once", round).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_allocation WHERE order_id = ? AND state <> 'RELEASED'", Integer.class, order))
                    .isZero();
            assertThat(lifecycle.expireOne(order)).as("repeated expiry is a no-op").isFalse();
        }
    }

    @Test
    void a_cash_order_has_no_payment_step() throws Exception {
        JsonNode order = ok(place(ok(quote(1, "COD"), 201)), 201);
        UUID id = UUID.fromString(order.get("id").asString());
        assertThat(order.get("payment").get("state").asString()).isEqualTo("NOT_REQUIRED");
        assertThat(report(id, customerSubject).getStatus()).isEqualTo(409);
        ok(cook(id, "accept"), 200);
        ok(cook(id, "prepare"), 200);
    }
}
