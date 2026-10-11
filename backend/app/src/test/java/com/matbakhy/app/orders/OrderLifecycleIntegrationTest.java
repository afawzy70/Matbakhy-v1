package com.matbakhy.app.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.MutableClock;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.orders.internal.OrderExpiryJob;
import com.matbakhy.orders.internal.OrderLifecycleService;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
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
 * TC-026..TC-031, TC-119 on PostgreSQL: version-guarded, actor-scoped transitions; terminal immutability; accept /
 * cancel / expire races; release exactly once; consumed stock after preparation; wrong fulfillment paths; secure
 * single-use handover codes with bounded attempts; audited MFA-gated ops override.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({TestLogins.class, OrderLifecycleIntegrationTest.Beans.class})
class OrderLifecycleIntegrationTest {

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
    String otherCookSubject;
    UUID customer;
    UUID kitchen;
    UUID zone;
    UUID meal;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).reset();
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        otherCookSubject = UUID.randomUUID().toString();
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, logins.login(cookSubject, "Cook").getPrincipal().id(), reviewer);
        TestKitchens.approvedKitchen(jdbc, logins.login(otherCookSubject, "Cook").getPrincipal().id(), reviewer);
        zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 10000, "فطير", "Feteer");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(customer, zone);
    }

    @AfterEach
    void resetClock() {
        ((MutableClock) clock).reset();
    }

    // ---- helpers ----

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "User");
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    /** Places an order for the customer (quantity 2) and returns its id. */
    private UUID order(boolean delivery) throws Exception {
        String q = "{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\"" + slot + "\",\"fulfillmentMode\":\""
                + (delivery ? "DELIVERY\",\"addressId\":\"" + address + "\"}" : "PICKUP\"}");
        MockHttpServletResponse quote = call(post("/api/v1/quotes").content(q), as(customerSubject));
        assertThat(quote.getStatus()).as(quote.getContentAsString()).isEqualTo(201);
        String place = """
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(body(quote).get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION);
        MockHttpServletResponse r = call(post("/api/v1/orders").header("Idempotency-Key", key()).content(place), as(customerSubject));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return UUID.fromString(body(r).get("id").asString());
    }

    private static String key() {
        return "cmd-" + UUID.randomUUID();
    }

    private long version(UUID order) {
        return jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
    }

    private String state(UUID order) {
        return jdbc.queryForObject("SELECT state FROM customer_order WHERE id = ?", String.class, order);
    }

    private MockHttpServletResponse cook(UUID order, String action, String extra) throws Exception {
        return cook(cookSubject, order, action, extra, key());
    }

    private MockHttpServletResponse cook(String subject, UUID order, String action, String extra, String k) throws Exception {
        return call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", k)
                .content("{\"expectedVersion\":" + version(order) + extra + "}"), as(subject));
    }

    private JsonNode cookOk(UUID order, String action, String extra) throws Exception {
        MockHttpServletResponse r = cook(order, action, extra);
        assertThat(r.getStatus()).as(action + ": " + r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private int[] counters() {
        return jdbc.queryForObject("SELECT reserved, allocated, consumed FROM meal_slot WHERE cook_slot_id = ? AND meal_id = ?",
                (rs, n) -> new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3)}, slot, meal);
    }

    private int[] slotCounters() {
        return jdbc.queryForObject("SELECT reserved, allocated, consumed FROM cook_slot WHERE id = ?",
                (rs, n) -> new int[] {rs.getInt(1), rs.getInt(2), rs.getInt(3)}, slot);
    }

    private String customerCode(UUID order) throws Exception {
        JsonNode v = body(call(get("/api/v1/orders/" + order), as(customerSubject)));
        return v.get("handoverCode").isNull() ? null : v.get("handoverCode").asString();
    }

    private UUID readyOrder(boolean delivery) throws Exception {
        UUID o = order(delivery);
        cookOk(o, "accept", "");
        cookOk(o, "prepare", "");
        cookOk(o, "ready", "");
        return o;
    }

    private UUID staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        staffSubjects.put(id, subject);
        return id;
    }

    private final java.util.Map<UUID, String> staffSubjects = new java.util.HashMap<>();

    // ---- happy paths and stock stages ----

    @Test
    void pickup_order_moves_through_every_stage_with_stock_and_events_in_step() throws Exception {
        UUID o = order(false);
        assertThat(counters()).containsExactly(2, 0, 0);
        JsonNode pending = body(call(get("/api/v1/cook/orders/" + o), as(cookSubject)));
        assertThat(pending.get("allowedActions").toString()).isEqualTo("[\"ACCEPT\",\"REJECT\"]");
        assertThat(pending.get("contact").isNull()).as("no customer contact before acceptance").isTrue();

        JsonNode accepted = cookOk(o, "accept", "");
        assertThat(accepted.get("state").asString()).isEqualTo("ACCEPTED");
        assertThat(accepted.get("contact").get("phone").asString()).isEqualTo("+201001112233");
        assertThat(counters()).containsExactly(0, 2, 0);
        assertThat(slotCounters()).containsExactly(0, 2, 0);
        JsonNode customerView = body(call(get("/api/v1/orders/" + o), as(customerSubject)));
        assertThat(customerView.get("contact").get("pickupLocation").asString()).as("pickup location after acceptance")
                .isEqualTo("Synthetic fixture location");

        cookOk(o, "prepare", "");
        assertThat(counters()).containsExactly(0, 0, 2);
        assertThat(customerCode(o)).isNull();
        JsonNode ready = cookOk(o, "ready", "");
        assertThat(ready.toString()).as("cook never sees the code").doesNotContain("handoverCode");
        String code = customerCode(o);
        assertThat(code).matches("[0-9]{6}");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover_challenge WHERE order_id = ? AND code_hash <> ?", Integer.class, o, code))
                .as("only a hash and ciphertext are stored").isEqualTo(1);

        JsonNode done = cookOk(o, "handover", ",\"code\":\"" + code + "\"");
        assertThat(done.get("state").asString()).isEqualTo("COMPLETED");
        assertThat(done.get("contact").isNull()).as("contact hidden after completion").isTrue();
        assertThat(counters()).as("consumed stays consumed").containsExactly(0, 0, 2);
        assertThat(customerCode(o)).isNull();
        JsonNode view = body(call(get("/api/v1/orders/" + o), as(customerSubject)));
        List<String> states = new ArrayList<>();
        view.get("events").forEach(e -> states.add(e.get("toState").asString()));
        assertThat(states).containsExactly("PENDING_ACCEPTANCE", "ACCEPTED", "PREPARING", "READY", "COMPLETED");
        assertThat(view.get("cashState").asString()).as("cash may still be due after completion").isEqualTo("DUE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ?", Integer.class, o)).isEqualTo(5);
    }

    // ---- TC-030: fulfillment paths ----

    @Test
    void wrong_fulfillment_paths_are_denied_and_the_correct_path_keeps_history() throws Exception {
        UUID pickup = readyOrder(false);
        MockHttpServletResponse dispatchPickup = cook(pickup, "dispatch", ",\"deliveryReference\":\"rider-1\"");
        assertThat(dispatchPickup.getStatus()).isEqualTo(409);
        assertThat(dispatchPickup.getContentAsString()).contains("WRONG_FULFILLMENT_MODE");
        assertThat(cook(pickup, "delivery-failure", ",\"reasonCode\":\"REFUSED\"").getContentAsString()).contains("WRONG_FULFILLMENT_MODE");

        UUID delivery = readyOrder(true);
        String code = customerCode(delivery);
        MockHttpServletResponse early = cook(delivery, "handover", ",\"code\":\"" + code + "\"");
        assertThat(early.getStatus()).as("delivery must be dispatched before completion").isEqualTo(409);
        assertThat(early.getContentAsString()).contains("WRONG_FULFILLMENT_MODE");
        assertThat(cook(delivery, "dispatch", "").getStatus()).as("delivery reference required").isEqualTo(422);
        cookOk(delivery, "dispatch", ",\"deliveryReference\":\"Synthetic rider 7\"");
        assertThat(customerCode(delivery)).as("code stays available out for delivery").isEqualTo(code);
        cookOk(delivery, "handover", ",\"code\":\"" + code + "\"");
        JsonNode view = body(call(get("/api/v1/orders/" + delivery), as(customerSubject)));
        List<String> states = new ArrayList<>();
        view.get("events").forEach(e -> states.add(e.get("toState").asString()));
        assertThat(states).containsExactly("PENDING_ACCEPTANCE", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY", "COMPLETED");
    }

    // ---- TC-031: handover code and override ----

    @Test
    void wrong_codes_are_bounded_valid_codes_work_once_and_locked_challenges_need_an_audited_override() throws Exception {
        UUID o = readyOrder(false);
        String code = customerCode(o);
        String wrong = code.equals("000000") ? "111111" : "000000";
        for (int i = 1; i <= 4; i++) {
            MockHttpServletResponse r = cook(o, "handover", ",\"code\":\"" + wrong + "\"");
            assertThat(r.getStatus()).isEqualTo(422);
            assertThat(r.getContentAsString()).contains("WRONG_CODE");
        }
        assertThat(cook(o, "handover", ",\"code\":\"" + wrong + "\"").getContentAsString()).contains("CHALLENGE_LOCKED");
        assertThat(jdbc.queryForObject("SELECT attempts FROM handover_challenge WHERE order_id = ?", Integer.class, o)).isEqualTo(5);
        MockHttpServletResponse afterLock = cook(o, "handover", ",\"code\":\"" + code + "\"");
        assertThat(afterLock.getStatus()).as("even the right code is refused once locked").isEqualTo(409);
        assertThat(afterLock.getContentAsString()).contains("CHALLENGE_LOCKED");
        assertThat(state(o)).isEqualTo("READY");
        assertThat(customerCode(o)).isNull();

        UUID noPermission = staff("order.ops");
        String override = "{\"expectedVersion\":" + version(o) + ",\"reason\":\"Customer identity checked by phone call\",\"evidenceRef\":\"case-123\"}";
        assertThat(call(post("/api/v1/ops/orders/" + o + "/handover-override").header("Idempotency-Key", key()).content(override),
                logins.loginWithMfa(staffSubjects.get(noPermission), "Staff")).getStatus()).isEqualTo(403);
        UUID overrider = staff("order.handover_override");
        MockHttpServletResponse noMfa = call(post("/api/v1/ops/orders/" + o + "/handover-override").header("Idempotency-Key", key())
                .content(override), as(staffSubjects.get(overrider)));
        assertThat(noMfa.getStatus()).isEqualTo(403);
        assertThat(noMfa.getContentAsString()).contains("STEP_UP_REQUIRED");
        MockHttpServletResponse ok = call(post("/api/v1/ops/orders/" + o + "/handover-override").header("Idempotency-Key", key())
                .content(override), logins.loginWithMfa(staffSubjects.get(overrider), "Staff"));
        assertThat(ok.getStatus()).as(ok.getContentAsString()).isEqualTo(200);
        assertThat(body(ok).get("state").asString()).isEqualTo("COMPLETED");
        assertThat(body(ok).get("handoverCode").isNull()).isTrue();
        assertThat(jdbc.queryForObject("SELECT reason_code FROM order_event WHERE order_id = ? AND to_state = 'COMPLETED'", String.class, o))
                .isEqualTo("HANDOVER_OVERRIDE");
        assertThat(jdbc.queryForObject("SELECT actor_type FROM order_event WHERE order_id = ? AND to_state = 'COMPLETED'", String.class, o))
                .isEqualTo("STAFF");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND action = 'order.handover_override' AND object_id = ?",
                Integer.class, overrider, o.toString())).isEqualTo(1);
    }

    @Test
    void a_valid_code_completes_once_and_cannot_be_replayed() throws Exception {
        UUID o = readyOrder(false);
        String code = customerCode(o);
        String k = key();
        MockHttpServletResponse first = cook(cookSubject, o, "handover", ",\"code\":\"" + code + "\"", k);
        assertThat(first.getStatus()).isEqualTo(200);
        MockHttpServletResponse replay = cook(cookSubject, o, "handover", ",\"code\":\"" + code + "\"", key());
        assertThat(replay.getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_event WHERE order_id = ? AND to_state = 'COMPLETED'", Integer.class, o)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM handover_attempt WHERE order_id = ? AND success", Integer.class, o)).isEqualTo(1);
    }

    @Test
    void handover_attempts_are_rate_limited_per_actor() throws Exception {
        UUID o = readyOrder(false);
        UUID cookId = as(cookSubject).getPrincipal().id();
        for (int i = 0; i < 30; i++) {
            jdbc.update("INSERT INTO handover_attempt (id, order_id, actor_id, success, attempted_at) VALUES (?, ?, ?, false, ?)",
                    UUID.randomUUID(), o, cookId, Timestamp.from(clock.instant()));
        }
        MockHttpServletResponse r = cook(o, "handover", ",\"code\":\"" + customerCode(o) + "\"");
        assertThat(r.getStatus()).isEqualTo(429);
        assertThat(state(o)).isEqualTo("READY");
    }

    // ---- TC-026: terminal immutability and versions ----

    @Test
    void terminal_orders_and_stale_versions_are_rejected_without_changing_history() throws Exception {
        UUID o = readyOrder(false);
        cookOk(o, "handover", ",\"code\":\"" + customerCode(o) + "\"");
        for (String action : List.of("accept", "prepare", "cancel")) {
            MockHttpServletResponse r = cook(o, action, ",\"reasonCode\":\"OTHER\"");
            assertThat(r.getStatus()).as(action).isEqualTo(409);
            assertThat(r.getContentAsString()).contains("COMPLETED");
        }
        MockHttpServletResponse patchStatus = call(patch("/api/v1/orders/" + o).content("{\"state\":\"CANCELLED\"}"), as(customerSubject));
        assertThat(patchStatus.getStatus()).as("no free-form status update route").isIn(404, 405);
        assertThatThrownBy(() -> jdbc.update("UPDATE customer_order SET state = 'CANCELLED' WHERE id = ?", o)).hasMessageContaining("immutable");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM order_event WHERE order_id = ?", Integer.class, o)).isEqualTo(5);

        UUID pending = order(false);
        MockHttpServletResponse stale = call(post("/api/v1/cook/orders/" + pending + "/accept").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + (version(pending) + 3) + "}"), as(cookSubject));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("VERSION_CONFLICT");
        assertThat(state(pending)).isEqualTo("PENDING_ACCEPTANCE");
    }

    // ---- TC-028: customer cancellation ----

    @Test
    void customers_cancel_pending_orders_directly_but_not_accepted_ones() throws Exception {
        UUID pending = order(false);
        MockHttpServletResponse noReason = call(post("/api/v1/orders/" + pending + "/cancel").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(pending) + ",\"reasonCode\":\"BECAUSE\"}"), as(customerSubject));
        assertThat(noReason.getStatus()).isEqualTo(422);
        MockHttpServletResponse cancelled = call(post("/api/v1/orders/" + pending + "/cancel").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(pending) + ",\"reasonCode\":\"CHANGED_MIND\"}"), as(customerSubject));
        assertThat(cancelled.getStatus()).as(cancelled.getContentAsString()).isEqualTo(200);
        assertThat(body(cancelled).get("state").asString()).isEqualTo("CANCELLED");
        assertThat(body(cancelled).get("cashState").asString()).isEqualTo("NOT_DUE");
        assertThat(counters()).containsExactly(0, 0, 0);

        UUID preparing = order(false);
        cookOk(preparing, "accept", "");
        cookOk(preparing, "prepare", "");
        MockHttpServletResponse denied = call(post("/api/v1/orders/" + preparing + "/cancel").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(preparing) + ",\"reasonCode\":\"CHANGED_MIND\"}"), as(customerSubject));
        assertThat(denied.getStatus()).isEqualTo(409);
        assertThat(denied.getContentAsString()).contains("CANCELLATION_REQUEST_REQUIRED");
        assertThat(state(preparing)).isEqualTo("PREPARING");
        String otherCustomer = UUID.randomUUID().toString();
        assertThat(call(post("/api/v1/orders/" + preparing + "/cancel").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(preparing) + ",\"reasonCode\":\"CHANGED_MIND\"}"), as(otherCustomer)).getStatus())
                .isEqualTo(404);
    }

    // ---- TC-029: release once; consumed stays consumed ----

    @Test
    void prepared_orders_cancelled_by_ops_keep_consumed_stock_and_never_release_twice() throws Exception {
        UUID o = order(false);
        cookOk(o, "accept", "");
        cookOk(o, "prepare", "");
        assertThat(counters()).containsExactly(0, 0, 2);
        UUID ops = staff("order.exception_cancel");
        String body = "{\"expectedVersion\":" + version(o) + ",\"reasonCode\":\"SAFETY\",\"caseId\":\"case-77\",\"note\":\"Food safety concern\"}";
        String k = key();
        MockHttpServletResponse first = call(post("/api/v1/ops/orders/" + o + "/exception-cancel").header("Idempotency-Key", k).content(body),
                as(staffSubjects.get(ops)));
        assertThat(first.getStatus()).as(first.getContentAsString()).isEqualTo(200);
        MockHttpServletResponse retry = call(post("/api/v1/ops/orders/" + o + "/exception-cancel").header("Idempotency-Key", k).content(body),
                as(staffSubjects.get(ops)));
        assertThat(retry.getStatus()).as("idempotent replay").isEqualTo(200);
        assertThat(call(post("/api/v1/ops/orders/" + o + "/exception-cancel").header("Idempotency-Key", key()).content(body),
                as(staffSubjects.get(ops))).getStatus()).as("terminal now").isEqualTo(409);
        assertThat(counters()).as("consumed portions are not restored automatically").containsExactly(0, 0, 2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_allocation WHERE order_id = ? AND state = 'RELEASED'", Integer.class, o)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND action = 'order.exception_cancel'", Integer.class, ops))
                .isEqualTo(1);
        assertThat(call(post("/api/v1/ops/orders/" + o + "/exception-cancel").header("Idempotency-Key", key()).content(body),
                as(customerSubject)).getStatus()).as("customers have no ops permission").isEqualTo(403);

        UUID rejected = order(false);
        assertThat(cook(rejected, "reject", "").getStatus()).as("reason required").isEqualTo(422);
        cookOk(rejected, "reject", ",\"reasonCode\":\"KITCHEN_BUSY\"");
        UUID accepted = order(false);
        cookOk(accepted, "accept", "");
        assertThat(counters()).containsExactly(0, 2, 2);
        cookOk(accepted, "cancel", ",\"reasonCode\":\"KITCHEN_ISSUE\"");
        assertThat(counters()).as("reject and cook cancel each released exactly once").containsExactly(0, 0, 2);
        assertThat(slotCounters()).containsExactly(0, 0, 2);
    }

    // ---- TC-027: races at the deadline and expiry ----

    @Test
    void accept_cancel_and_expire_racing_produce_one_legal_outcome_and_at_most_one_release() throws Exception {
        for (int round = 0; round < 4; round++) {
            ((MutableClock) clock).reset();
            UUID o = order(false);
            Instant acceptBy = jdbc.queryForObject("SELECT accept_by FROM customer_order WHERE id = ?", Timestamp.class, o).toInstant();
            boolean pastDeadline = round % 2 == 1;
            ((MutableClock) clock).advance(Duration.between(clock.instant(), pastDeadline ? acceptBy.plusSeconds(1) : acceptBy.minusSeconds(30)));
            long v = version(o);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newFixedThreadPool(3);
            List<Future<Object>> results = new ArrayList<>();
            List<Callable<Object>> tasks = List.of(
                    () -> {
                        start.await();
                        return call(post("/api/v1/cook/orders/" + o + "/accept").header("Idempotency-Key", key())
                                .content("{\"expectedVersion\":" + v + "}"), as(cookSubject)).getStatus();
                    },
                    () -> {
                        start.await();
                        return call(post("/api/v1/orders/" + o + "/cancel").header("Idempotency-Key", key())
                                .content("{\"expectedVersion\":" + v + ",\"reasonCode\":\"TOO_SLOW\"}"), as(customerSubject)).getStatus();
                    },
                    () -> {
                        start.await();
                        return lifecycle.expireOne(o);
                    });
            try {
                for (Callable<Object> t : tasks) {
                    results.add(pool.submit(t));
                }
                start.countDown();
                for (Future<Object> f : results) {
                    f.get();
                }
            } finally {
                pool.shutdownNow();
            }
            String finalState = state(o);
            List<String> transitions = jdbc.queryForList("SELECT to_state FROM order_event WHERE order_id = ? AND sequence > 1", String.class, o);
            assertThat(transitions).as("exactly one transition, round " + round).hasSize(1);
            assertThat(transitions.getFirst()).isEqualTo(finalState);
            if (pastDeadline) {
                assertThat(finalState).isIn("EXPIRED", "CANCELLED");
            } else {
                assertThat(finalState).isIn("ACCEPTED", "CANCELLED");
            }
            int released = jdbc.queryForObject("SELECT count(*) FROM stock_allocation WHERE order_id = ? AND state = 'RELEASED'", Integer.class, o);
            assertThat(released).isLessThanOrEqualTo(1);
            int[] c = counters();
            assertThat(c[0]).isZero();
            assertThat(c[1]).isEqualTo(finalState.equals("ACCEPTED") ? 2 : 0);
            if (finalState.equals("ACCEPTED")) {
                cookOk(o, "cancel", ",\"reasonCode\":\"OTHER\"");
            }
        }
    }

    @Test
    void the_expiry_job_expires_overdue_orders_once_by_server_time() throws Exception {
        UUID o = order(false);
        OrderExpiryJob job = new OrderExpiryJob(lifecycle);
        assertThat(job.expireDue()).isZero();
        Instant acceptBy = jdbc.queryForObject("SELECT accept_by FROM customer_order WHERE id = ?", Timestamp.class, o).toInstant();
        ((MutableClock) clock).advance(Duration.between(clock.instant(), acceptBy));
        assertThat(job.expireDue()).isGreaterThanOrEqualTo(1);
        assertThat(state(o)).isEqualTo("EXPIRED");
        assertThat(job.expireDue()).isZero();
        assertThat(counters()).containsExactly(0, 0, 0);
        assertThat(jdbc.queryForObject("SELECT state FROM cash_obligation WHERE order_id = ?", String.class, o)).isEqualTo("NOT_DUE");
        MockHttpServletResponse late = cook(o, "accept", "");
        assertThat(late.getStatus()).isEqualTo(409);
    }

    // ---- scope and suspension ----

    @Test
    void cooks_act_only_on_their_kitchen_and_suspension_blocks_acceptance_but_not_fulfillment() throws Exception {
        UUID o = order(true);
        assertThat(call(get("/api/v1/cook/orders/" + o), as(otherCookSubject)).getStatus()).isEqualTo(404);
        assertThat(cook(otherCookSubject, o, "accept", "", key()).getStatus()).isEqualTo(404);
        assertThat(call(get("/api/v1/cook/orders"), as(customerSubject)).getStatus()).as("customers are not cooks").isEqualTo(403);
        JsonNode queue = body(call(get("/api/v1/cook/orders").param("state", "PENDING_ACCEPTANCE"), as(cookSubject)));
        assertThat(queue.toString()).contains(o.toString());
        assertThat(body(call(get("/api/v1/cook/orders"), as(otherCookSubject))).toString()).doesNotContain(o.toString());

        UUID accepted = order(false);
        cookOk(accepted, "accept", "");
        jdbc.update("UPDATE kitchen SET operating_state = 'SUSPENDED' WHERE id = ?", kitchen);
        MockHttpServletResponse blocked = cook(o, "accept", "");
        assertThat(blocked.getStatus()).isEqualTo(409);
        assertThat(blocked.getContentAsString()).contains("KITCHEN_UNAVAILABLE");
        cookOk(accepted, "prepare", "");
        cookOk(accepted, "ready", "");
        jdbc.update("UPDATE kitchen SET operating_state = 'ACTIVE' WHERE id = ?", kitchen);
    }
}
