package com.matbakhy.app.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.notifications.api.NotificationChannels.EmailSender;
import com.matbakhy.notifications.api.NotificationChannels.PushSender;
import com.matbakhy.notifications.api.NotificationChannels.Result;
import com.matbakhy.notifications.internal.DeliveryWorker;
import com.matbakhy.notifications.internal.OutboxDispatcher;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
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
 * TC-037, TC-038, TC-121 on PostgreSQL: provider outages never touch the committed order or inbox, retries are bounded
 * and dead letters visible to operators; leased workers and duplicate events yield one inbox record and one effective
 * send per channel; inbox read state and encrypted device registrations behave per account.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({TestLogins.class, NotificationIntegrationTest.Beans.class})
class NotificationIntegrationTest {

    /** Test-only controllable channel providers; they record every call by delivery id. */
    static final class Channels {
        enum Mode { OK, THROW, PERMANENT }

        final AtomicReference<Mode> mode = new AtomicReference<>(Mode.OK);
        final List<UUID> emailCalls = new CopyOnWriteArrayList<>();
        final Map<UUID, String> pushTokens = new ConcurrentHashMap<>();

        Result answer(UUID deliveryId) {
            return switch (mode.get()) {
                case OK -> Result.ok("test-" + deliveryId);
                case PERMANENT -> Result.permanentFailure("REJECTED_BY_TEST_PROVIDER");
                case THROW -> throw new IllegalStateException("test provider timeout");
            };
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        Channels testChannels() {
            return new Channels();
        }

        @Bean
        @Primary
        EmailSender testEmailSender(Channels channels) {
            return (deliveryId, accountId, locale, template, params) -> {
                channels.emailCalls.add(deliveryId);
                return channels.answer(deliveryId);
            };
        }

        @Bean
        @Primary
        PushSender testPushSender(Channels channels) {
            return (deliveryId, platform, token, template, params) -> {
                channels.pushTokens.put(deliveryId, token);
                return channels.answer(deliveryId);
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired Channels channels;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired DeliveryWorker worker;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    UUID customer;
    UUID cook;
    UUID meal;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        channels.mode.set(Channels.Mode.OK);
        channels.emailCalls.clear();
        channels.pushTokens.clear();
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        UUID kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        UUID zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 12000, "فتة", "Fatta");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(customer, zone);
    }

    @AfterEach
    void dropInjectedFailure() {
        jdbc.execute("DROP TRIGGER IF EXISTS test_fail_notification ON notification");
        jdbc.execute("DROP FUNCTION IF EXISTS test_fail_notification()");
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
        return r.getContentAsString().isEmpty() ? null : json.readTree(r.getContentAsString());
    }

    private static String key() {
        return "ntf-" + UUID.randomUUID();
    }

    private long version(UUID order) {
        return jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
    }

    private UUID placeOrder() throws Exception {
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), as(customerSubject)), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", key()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), as(customerSubject)), 201);
        return UUID.fromString(order.get("id").asString());
    }

    private void cookCommand(UUID order, String action) throws Exception {
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(order) + "}"), as(cookSubject)), 200);
    }

    private String staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        return subject;
    }

    private void drainOutbox() {
        while (dispatcher.dispatchOnce("worker-test", 100) > 0) {
            // keep going until nothing due is left
        }
    }

    private UUID delivery(UUID recipient, UUID order, String type, String channel) {
        return jdbc.queryForObject("""
                SELECT d.id FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                WHERE n.recipient_id = ? AND n.order_id = ? AND n.type = ? AND d.channel = ?""", UUID.class, recipient, order, type, channel);
    }

    private Map<String, Object> deliveryRow(UUID id) {
        return jdbc.queryForMap("SELECT status, attempts, next_attempt_at, last_error FROM notification_delivery WHERE id = ?", id);
    }

    private void makeDue(UUID deliveryId) {
        jdbc.update("UPDATE notification_delivery SET next_attempt_at = now() - interval '1 second' WHERE id = ?", deliveryId);
    }

    private UUID outboxEvent(UUID order, String type) {
        return jdbc.queryForObject("SELECT id FROM outbox_event WHERE aggregate_id = ? AND event_type = ?", UUID.class, order, type);
    }

    private static <T> List<T> concurrently(Callable<T> a, Callable<T> b) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<T> fa = pool.submit(() -> {
                start.await();
                return a.call();
            });
            Future<T> fb = pool.submit(() -> {
                start.await();
                return b.call();
            });
            start.countDown();
            return List.of(fa.get(), fb.get());
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- TC-037: provider outage, bounded retry, visible dead letter ----

    @Test
    void provider_outage_keeps_order_and_inbox_and_dead_letters_after_bounded_retries() throws Exception {
        channels.mode.set(Channels.Mode.THROW);
        UUID order = placeOrder();
        long orderVersion = version(order);
        drainOutbox();
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, outboxEvent(order, "ORDER_PLACED")))
                .isEqualTo("SENT");
        UUID email = delivery(cook, order, "ORDER_PLACED", "EMAIL");

        worker.deliverOnce("worker-test", 100);
        Map<String, Object> first = deliveryRow(email);
        assertThat(first.get("status")).isEqualTo("PENDING");
        assertThat(first.get("attempts")).isEqualTo(1);
        assertThat(first.get("last_error")).isEqualTo("IllegalStateException");
        assertThat(((java.sql.Timestamp) first.get("next_attempt_at")).toInstant())
                .as("first backoff is the initial delay").isAfter(clock.instant().plusSeconds(25));
        int callsBefore = (int) channels.emailCalls.stream().filter(email::equals).count();
        worker.deliverOnce("worker-test", 100);
        assertThat(channels.emailCalls.stream().filter(email::equals).count()).as("not retried before its backoff").isEqualTo(callsBefore);

        for (int attempt = 2; attempt <= 5; attempt++) {
            makeDue(email);
            worker.deliverOnce("worker-test", 100);
        }
        assertThat(deliveryRow(email).get("status")).isEqualTo("DEAD");
        assertThat(deliveryRow(email).get("attempts")).isEqualTo(5);
        makeDue(email);
        worker.deliverOnce("worker-test", 100);
        assertThat(channels.emailCalls.stream().filter(email::equals).count()).as("retries bounded at the maximum").isEqualTo(5);

        // The order and the inbox are untouched by the outage.
        assertThat(version(order)).isEqualTo(orderVersion);
        assertThat(ok(call(get("/api/v1/orders/" + order), as(customerSubject)), 200).get("state").asString()).isEqualTo("PENDING_ACCEPTANCE");
        JsonNode inbox = ok(call(get("/api/v1/notifications"), as(cookSubject)), 200);
        assertThat(inbox.get("items").size()).isEqualTo(1);
        assertThat(inbox.get("items").get(0).get("type").asString()).isEqualTo("ORDER_PLACED");
        assertThat(inbox.get("items").get(0).get("orderId").asString()).isEqualTo(order.toString());

        // Operators see the dead letter, redacted.
        String ops = staff("integration.ops");
        JsonNode health = ok(call(get("/api/v1/ops/integrations"), as(ops)), 200);
        JsonNode failure = null;
        for (JsonNode f : health.get("recentFailures")) {
            if (f.get("deliveryId").asString().equals(email.toString())) {
                failure = f;
            }
        }
        assertThat(failure).as("dead letter listed").isNotNull();
        assertThat(failure.get("attempts").asInt()).isEqualTo(5);
        assertThat(failure.get("lastError").asString()).isEqualTo("IllegalStateException");
        assertThat(failure.toString()).doesNotContain(cook.toString()).doesNotContain(customer.toString());
        JsonNode emailHealth = null;
        for (JsonNode c : health.get("channels")) {
            if (c.get("channel").asString().equals("EMAIL")) {
                emailHealth = c;
            }
        }
        assertThat(emailHealth.get("dead").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(call(get("/api/v1/ops/integrations"), as(customerSubject)).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/integrations"), as(staff("finance.read"))).getStatus()).isEqualTo(403);
    }

    @Test
    void a_permanent_provider_rejection_is_dead_at_once() throws Exception {
        channels.mode.set(Channels.Mode.PERMANENT);
        UUID order = placeOrder();
        drainOutbox();
        UUID email = delivery(cook, order, "ORDER_PLACED", "EMAIL");
        worker.deliverOnce("worker-test", 100);
        assertThat(deliveryRow(email).get("status")).isEqualTo("DEAD");
        assertThat(deliveryRow(email).get("attempts")).isEqualTo(1);
        assertThat(deliveryRow(email).get("last_error")).isEqualTo("REJECTED_BY_TEST_PROVIDER");
    }

    @Test
    void a_failing_outbox_event_is_retried_bounded_then_replayed_by_an_operator() throws Exception {
        UUID order = placeOrder();
        UUID event = outboxEvent(order, "ORDER_PLACED");
        jdbc.execute("""
                CREATE FUNCTION test_fail_notification() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.order_id = '%s'::uuid THEN RAISE EXCEPTION 'injected inbox failure'; END IF;
                  RETURN NEW;
                END $$""".formatted(order));
        jdbc.execute("CREATE TRIGGER test_fail_notification BEFORE INSERT ON notification FOR EACH ROW EXECUTE FUNCTION test_fail_notification()");
        for (int attempt = 1; attempt <= 5; attempt++) {
            jdbc.update("UPDATE outbox_event SET available_at = now() - interval '1 second' WHERE id = ?", event);
            dispatcher.dispatchOnce("worker-test", 100);
        }
        Map<String, Object> failed = jdbc.queryForMap("SELECT status, attempts, last_error, version FROM outbox_event WHERE id = ?", event);
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(failed.get("attempts")).isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE order_id = ?", Integer.class, order))
                .as("failed fan-out rolled back").isZero();
        assertThat(ok(call(get("/api/v1/orders/" + order), as(customerSubject)), 200).get("state").asString()).isEqualTo("PENDING_ACCEPTANCE");
        jdbc.update("UPDATE outbox_event SET available_at = now() - interval '1 second' WHERE id = ?", event);
        dispatcher.dispatchOnce("worker-test", 100);
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_event WHERE id = ?", Integer.class, event)).as("dead letter not retried").isEqualTo(5);

        String ops = staff("integration.ops");
        assertThat(ok(call(get("/api/v1/ops/integrations"), as(ops)), 200).get("outboxFailed").asInt()).isGreaterThanOrEqualTo(1);
        long v = (Long) failed.get("version");
        String retryUrl = "/api/v1/ops/outbox/" + event + "/retry";
        assertThat(call(post(retryUrl).header("Idempotency-Key", key()).content("{\"reason\":\"provider fixed\",\"expectedVersion\":" + (v - 1) + "}"),
                as(ops)).getStatus()).as("stale version").isEqualTo(409);
        assertThat(call(post(retryUrl).header("Idempotency-Key", key()).content("{\"reason\":\"provider fixed\",\"expectedVersion\":" + v + "}"),
                as(customerSubject)).getStatus()).isEqualTo(403);

        dropInjectedFailure();
        String retryKey = key();
        JsonNode retried = ok(call(post(retryUrl).header("Idempotency-Key", retryKey)
                .content("{\"reason\":\"provider fixed\",\"expectedVersion\":" + v + "}"), as(ops)), 200);
        assertThat(retried.get("status").asString()).isEqualTo("PENDING");
        assertThat(retried.get("attempts").asInt()).isZero();
        JsonNode replay = ok(call(post(retryUrl).header("Idempotency-Key", retryKey)
                .content("{\"reason\":\"provider fixed\",\"expectedVersion\":" + v + "}"), as(ops)), 200);
        assertThat(replay.get("version").asLong()).as("idempotent replay").isEqualTo(retried.get("version").asLong());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'outbox.retry' AND object_id = ?", Integer.class,
                event.toString())).isEqualTo(1);
        assertThat(call(post(retryUrl).header("Idempotency-Key", key()).content("{\"reason\":\"again\",\"expectedVersion\":"
                + retried.get("version").asLong() + "}"), as(ops)).getStatus()).as("only FAILED events").isEqualTo(409);

        drainOutbox();
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, event)).isEqualTo("SENT");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE order_id = ? AND recipient_id = ?", Integer.class, order, cook))
                .isEqualTo(1);
    }

    // ---- TC-038: leased workers, duplicate and out-of-order events ----

    @Test
    void two_workers_and_duplicate_events_produce_one_inbox_record_and_one_send() throws Exception {
        UUID order = placeOrder();
        cookCommand(order, "accept");
        cookCommand(order, "prepare");
        long orderVersion = version(order);
        UUID accepted = outboxEvent(order, "ORDER_ACCEPTED");
        // A duplicate of ORDER_ACCEPTED delivered after the newer PREPARING event (duplicate and out of order).
        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, available_at, created_at)
                SELECT ?, aggregate_type, aggregate_id, event_type, payload, now(), now() + interval '1 second' FROM outbox_event WHERE id = ?""",
                UUID.randomUUID(), accepted);

        List<Integer> dispatched = concurrently(() -> dispatcher.dispatchOnce("worker-a", 100), () -> dispatcher.dispatchOnce("worker-b", 100));
        drainOutbox();
        assertThat(dispatched.get(0) + dispatched.get(1)).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ? AND status <> 'SENT'", Integer.class, order)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE recipient_id = ? AND order_id = ? AND type = 'ORDER_ACCEPTED'",
                Integer.class, customer, order)).as("one inbox record per recipient and event").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE order_id = ?", Integer.class, order))
                .as("cook: PLACED; customer: ACCEPTED, PREPARING").isEqualTo(3);
        assertThat(version(order)).as("notifications never write order state").isEqualTo(orderVersion);

        List<Integer> sent = concurrently(() -> worker.deliverOnce("worker-a", 100), () -> worker.deliverOnce("worker-b", 100));
        assertThat(sent.get(0) + sent.get(1)).isGreaterThanOrEqualTo(3);
        for (String type : List.of("ORDER_ACCEPTED", "ORDER_PREPARING")) {
            UUID email = delivery(customer, order, type, "EMAIL");
            assertThat(channels.emailCalls.stream().filter(email::equals).count()).as(type + " sent once").isEqualTo(1);
            assertThat(deliveryRow(email).get("status")).isEqualTo("SENT");
        }
        // The client resolves the current server state rather than trusting notification order.
        JsonNode inbox = ok(call(get("/api/v1/notifications"), as(customerSubject)), 200);
        assertThat(inbox.get("items").size()).isEqualTo(2);
        assertThat(ok(call(get("/api/v1/orders/" + order), as(customerSubject)), 200).get("state").asString()).isEqualTo("PREPARING");
    }

    @Test
    void an_expired_lease_is_reclaimed_and_a_live_lease_is_respected() throws Exception {
        UUID order = placeOrder();
        UUID event = outboxEvent(order, "ORDER_PLACED");
        jdbc.update("UPDATE outbox_event SET lease_owner = 'crashed-worker', leased_until = now() + interval '5 minutes' WHERE id = ?", event);
        drainOutbox();
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, event)).as("live lease respected").isEqualTo("PENDING");
        jdbc.update("UPDATE outbox_event SET leased_until = now() - interval '1 second' WHERE id = ?", event);
        drainOutbox();
        assertThat(jdbc.queryForObject("SELECT status FROM outbox_event WHERE id = ?", String.class, event)).isEqualTo("SENT");

        UUID email = delivery(cook, order, "ORDER_PLACED", "EMAIL");
        jdbc.update("UPDATE notification_delivery SET lease_owner = 'crashed-worker', leased_until = now() + interval '5 minutes' WHERE id = ?", email);
        worker.deliverOnce("worker-b", 100);
        assertThat(channels.emailCalls).doesNotContain(email);
        jdbc.update("UPDATE notification_delivery SET leased_until = now() - interval '1 second' WHERE id = ?", email);
        worker.deliverOnce("worker-b", 100);
        assertThat(channels.emailCalls.stream().filter(email::equals).count()).isEqualTo(1);
        // A late result from the crashed worker cannot overwrite the reclaimed delivery.
        assertThat(jdbc.update("UPDATE notification_delivery SET status = 'DEAD' WHERE id = ? AND lease_owner = 'crashed-worker' AND status = 'PENDING'",
                email)).isZero();
        assertThat(deliveryRow(email).get("status")).isEqualTo("SENT");
    }

    // ---- TC-121: inbox read state and device registrations ----

    @Test
    void inbox_pages_marks_read_per_owner_and_filters_unread() throws Exception {
        UUID order = placeOrder();
        cookCommand(order, "accept");
        cookCommand(order, "prepare");
        cookCommand(order, "ready");
        // Dispatch out of order: the newest event first, the older ones afterwards.
        jdbc.update("UPDATE outbox_event SET available_at = now() + interval '1 hour' WHERE aggregate_id = ? AND event_type <> 'ORDER_READY'", order);
        drainOutbox();
        jdbc.update("UPDATE outbox_event SET available_at = now() - interval '1 second' WHERE aggregate_id = ? AND status = 'PENDING'", order);
        drainOutbox();

        JsonNode page1 = ok(call(get("/api/v1/notifications").param("limit", "2"), as(customerSubject)), 200);
        assertThat(page1.get("items").size()).isEqualTo(2);
        assertThat(page1.get("hasMore").asBoolean()).isTrue();
        JsonNode page2 = ok(call(get("/api/v1/notifications").param("limit", "2").param("cursor", page1.get("nextCursor").asString()),
                as(customerSubject)), 200);
        assertThat(page2.get("items").size()).isEqualTo(1);
        assertThat(page2.get("hasMore").asBoolean()).isFalse();
        assertThat(List.of(page1.get("items").get(0).get("type").asString(), page1.get("items").get(1).get("type").asString(),
                page2.get("items").get(0).get("type").asString())).as("inbox follows event order, not dispatch order")
                .containsExactly("ORDER_READY", "ORDER_PREPARING", "ORDER_ACCEPTED");
        JsonNode newest = page1.get("items").get(0);
        assertThat(newest.get("type").asString()).isEqualTo("ORDER_READY");
        assertThat(newest.get("params").get("state").asString()).isEqualTo("READY");
        assertThat(newest.get("params").has("orderNumber")).isTrue();
        assertThat(newest.get("readAt").isNull()).isTrue();

        String id = newest.get("id").asString();
        assertThat(call(put("/api/v1/notifications/" + id + "/read"), as(cookSubject)).getStatus()).as("not the owner").isEqualTo(404);
        JsonNode read = ok(call(put("/api/v1/notifications/" + id + "/read"), as(customerSubject)), 200);
        assertThat(read.get("readAt").isNull()).isFalse();
        JsonNode again = ok(call(put("/api/v1/notifications/" + id + "/read"), as(customerSubject)), 200);
        assertThat(again.get("readAt").asString()).as("idempotent").isEqualTo(read.get("readAt").asString());
        JsonNode unread = ok(call(get("/api/v1/notifications").param("unreadOnly", "true"), as(customerSubject)), 200);
        assertThat(unread.get("items").size()).isEqualTo(2);
        assertThat(ok(call(get("/api/v1/notifications"), as(cookSubject)), 200).get("items").size()).as("own inbox only").isEqualTo(1);
        assertThat(call(get("/api/v1/notifications").param("cursor", "%%%"), as(customerSubject)).getStatus()).isEqualTo(422);
        assertThat(call(get("/api/v1/notifications").param("limit", "101"), as(customerSubject)).getStatus()).isEqualTo(422);
        assertThat(mvc.perform(get("/api/v1/notifications")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void device_tokens_are_encrypted_used_for_push_and_revoked_on_unregister_or_account_switch() throws Exception {
        String installation = "inst-" + UUID.randomUUID().toString().substring(0, 12);
        String token = "synthetic-push-token-" + UUID.randomUUID();
        String body = "{\"platform\":\"ANDROID\",\"pushToken\":\"" + token + "\",\"environment\":\"DEVELOPMENT\",\"appVersion\":\"1.0.0\"}";
        JsonNode reg = ok(call(put("/api/v1/me/devices/" + installation).content(body), as(customerSubject)), 200);
        assertThat(reg.toString()).doesNotContain(token);
        Map<String, Object> row = jdbc.queryForMap("SELECT token_hash, token_ciphertext FROM device_registration WHERE installation_id = ? AND revoked_at IS NULL",
                installation);
        assertThat((String) row.get("token_hash")).doesNotContain(token);
        assertThat(new String((byte[]) row.get("token_ciphertext"), StandardCharsets.ISO_8859_1)).doesNotContain("synthetic-push-token");
        assertThat(call(put("/api/v1/me/devices/" + installation).content(body.replace("ANDROID", "WINDOWS")), as(customerSubject)).getStatus())
                .isEqualTo(422);
        assertThat(call(put("/api/v1/me/devices/bad").content(body), as(customerSubject)).getStatus()).isEqualTo(422);

        UUID order = placeOrder();
        cookCommand(order, "accept");
        drainOutbox();
        UUID push = delivery(customer, order, "ORDER_ACCEPTED", "PUSH");
        worker.deliverOnce("worker-test", 100);
        assertThat(channels.pushTokens.get(push)).as("decrypted only for the provider call").isEqualTo(token);
        assertThat(deliveryRow(push).get("status")).isEqualTo("SENT");

        // Re-registering the installation under another account revokes the first registration.
        ok(call(put("/api/v1/me/devices/" + installation).content(body), as(cookSubject)), 200);
        assertThat(jdbc.queryForObject("SELECT user_id FROM device_registration WHERE installation_id = ? AND revoked_at IS NULL", UUID.class,
                installation)).isEqualTo(cook);
        assertThat(call(delete("/api/v1/me/devices/" + installation), as(customerSubject)).getStatus()).as("no longer the customer's").isEqualTo(404);
        cookCommand(order, "prepare");
        drainOutbox();
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                WHERE n.order_id = ? AND n.type = 'ORDER_PREPARING' AND d.channel = 'PUSH'""", Integer.class, order))
                .as("customer has no device any more").isZero();

        assertThat(call(delete("/api/v1/me/devices/" + installation), as(cookSubject)).getStatus()).isEqualTo(204);
        assertThat(call(delete("/api/v1/me/devices/" + installation), as(cookSubject)).getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_registration WHERE installation_id = ? AND revoked_at IS NULL", Integer.class,
                installation)).isZero();
    }

    @Test
    void a_push_for_a_device_revoked_after_fan_out_is_not_sent() throws Exception {
        String installation = "inst-" + UUID.randomUUID().toString().substring(0, 12);
        ok(call(put("/api/v1/me/devices/" + installation).content("{\"platform\":\"IOS\",\"pushToken\":\"synthetic-ios-token-" + UUID.randomUUID()
                + "\",\"environment\":\"DEVELOPMENT\",\"appVersion\":\"1.0.0\"}"), as(customerSubject)), 200);
        UUID order = placeOrder();
        cookCommand(order, "accept");
        drainOutbox();
        UUID push = delivery(customer, order, "ORDER_ACCEPTED", "PUSH");
        assertThat(call(delete("/api/v1/me/devices/" + installation), as(customerSubject)).getStatus()).isEqualTo(204);
        worker.deliverOnce("worker-test", 100);
        assertThat(channels.pushTokens).doesNotContainKey(push);
        assertThat(deliveryRow(push).get("status")).isEqualTo("DEAD");
        assertThat(deliveryRow(push).get("last_error")).isEqualTo("DEVICE_UNREGISTERED");
    }
}
