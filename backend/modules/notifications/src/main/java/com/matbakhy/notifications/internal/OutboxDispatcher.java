package com.matbakhy.notifications.internal;

import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.orders.api.OrderDirectory;
import com.matbakhy.orders.api.OrderDirectory.Parties;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import com.matbakhy.shared.web.RequestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns outbox events into inbox notifications and channel deliveries (BR-037, BR-038). Events are claimed with a
 * lease (FOR UPDATE SKIP LOCKED), so several workers share the work and a crashed worker's events become claimable
 * again when its lease expires. Each event is processed in its own transaction; inbox rows are unique per recipient
 * and logical event, so duplicates and replays add nothing. Nothing here writes order state.
 */
@Component
public class OutboxDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxDispatcher.class);

    static final Set<String> CUSTOMER_EVENTS = Set.of("ORDER_ACCEPTED", "ORDER_PREPARING", "ORDER_READY", "ORDER_OUT_FOR_DELIVERY",
            "ORDER_COMPLETED", "ORDER_REJECTED", "ORDER_EXPIRED", "ORDER_DELIVERY_FAILED", "ORDER_CANCELLED");
    static final Set<String> COOK_EVENTS = Set.of("ORDER_PLACED", "ORDER_CANCELLED", "ORDER_EXPIRED");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final OrderDirectory orders;
    private final KitchenDirectory kitchens;
    private final NotificationProperties props;
    private final JsonMapper json;
    private final Clock clock;

    OutboxDispatcher(JdbcTemplate jdbc, PlatformTransactionManager txm, OrderDirectory orders, KitchenDirectory kitchens,
                     NotificationProperties props, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.orders = orders;
        this.kitchens = kitchens;
        this.props = props;
        this.json = json;
        this.clock = clock;
    }

    /** Claims and processes up to {@code limit} due events; returns how many were dispatched. */
    public int dispatchOnce(String worker, int limit) {
        Instant now = clock.instant();
        List<Map<String, Object>> claimed = tx.execute(s -> jdbc.queryForList("""
                UPDATE outbox_event SET lease_owner = ?, leased_until = ? WHERE id IN (
                    SELECT id FROM outbox_event WHERE status = 'PENDING' AND available_at <= ? AND (leased_until IS NULL OR leased_until < ?)
                    ORDER BY created_at, id LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id, aggregate_type, aggregate_id, event_type, payload::text AS payload, attempts, created_at, request_id""",
                worker, Timestamp.from(now.plus(props.lease())), Timestamp.from(now), Timestamp.from(now), limit));
        // RETURNING does not keep the subquery order; process in occurrence order.
        claimed.sort(Comparator.comparing((Map<String, Object> e) -> (Timestamp) e.get("created_at")).thenComparing(e -> (UUID) e.get("id")));
        int done = 0;
        for (Map<String, Object> e : claimed) {
            UUID id = (UUID) e.get("id");
            try {
                Boolean ok = tx.execute(s -> {
                    fanOut(id, (String) e.get("event_type"), (UUID) e.get("aggregate_id"), (String) e.get("payload"),
                            ((Timestamp) e.get("created_at")).toInstant());
                    return jdbc.update("""
                            UPDATE outbox_event SET status = 'SENT', processed_at = ?, lease_owner = NULL, leased_until = NULL, version = version + 1
                            WHERE id = ? AND lease_owner = ? AND status = 'PENDING'""", Timestamp.from(clock.instant()), id, worker) == 1;
                });
                if (Boolean.TRUE.equals(ok)) {
                    done++;
                }
            } catch (RuntimeException failure) {
                fail(id, worker, ((Integer) e.get("attempts")) + 1, failure, (String) e.get("request_id"));
            }
        }
        return done;
    }

    /** Inbox rows carry the event's occurrence time, so the inbox follows event order whatever the processing order. */
    private void fanOut(UUID eventId, String type, UUID aggregateId, String payload, Instant occurredAt) {
        Parties parties = orders.parties(aggregateId).orElse(null);
        if (parties == null) {
            return;
        }
        JsonNode p = json.readTree(payload);
        String state = p.has("state") ? p.get("state").asString() : null;
        List<UUID> recipients = new ArrayList<>();
        if (CUSTOMER_EVENTS.contains(type)) {
            recipients.add(parties.customerId());
        }
        if (COOK_EVENTS.contains(type)) {
            kitchens.find(parties.kitchenId()).ifPresent(k -> recipients.add(k.ownerId()));
        }
        Instant now = clock.instant();
        String params = json.writeValueAsString(state == null ? Map.of("orderNumber", parties.publicNumber())
                : Map.of("orderNumber", parties.publicNumber(), "state", state));
        for (UUID recipient : recipients) {
            UUID notification = UUID.randomUUID();
            int inserted = jdbc.update("""
                    INSERT INTO notification (id, recipient_id, dedup_key, source_event_id, type, order_id, params, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?) ON CONFLICT (recipient_id, dedup_key) DO NOTHING""",
                    notification, recipient, type + ":" + aggregateId, eventId, type, parties.orderId(), params, Timestamp.from(occurredAt));
            if (inserted == 0) {
                continue;
            }
            jdbc.update("""
                    INSERT INTO notification_delivery (id, notification_id, channel, target, next_attempt_at)
                    VALUES (?, ?, 'EMAIL', 'account', ?) ON CONFLICT DO NOTHING""", UUID.randomUUID(), notification, Timestamp.from(now));
            for (String installation : jdbc.queryForList("SELECT installation_id FROM device_registration WHERE user_id = ? AND revoked_at IS NULL",
                    String.class, recipient)) {
                jdbc.update("""
                        INSERT INTO notification_delivery (id, notification_id, channel, target, next_attempt_at)
                        VALUES (?, ?, 'PUSH', ?, ?) ON CONFLICT DO NOTHING""", UUID.randomUUID(), notification, installation, Timestamp.from(now));
            }
        }
    }

    private void fail(UUID id, String worker, int attempts, RuntimeException failure, String requestId) {
        boolean dead = attempts >= props.maxAttempts();
        Instant next = clock.instant().plus(backoff(attempts));
        String error = failure.getClass().getSimpleName();
        try (var traced = MDC.putCloseable(RequestContext.REQUEST_ID_MDC_KEY, requestId)) {
            LOG.warn("outbox event {} dispatch failed on attempt {}: {}{}", id, attempts, error, dead ? " (moved to FAILED)" : "");
        }
        tx.executeWithoutResult(s -> jdbc.update("""
                UPDATE outbox_event SET attempts = ?, status = ?, available_at = ?, last_error = ?, lease_owner = NULL, leased_until = NULL,
                       version = version + 1 WHERE id = ? AND lease_owner = ?""",
                attempts, dead ? "FAILED" : "PENDING", Timestamp.from(next), error, id, worker));
    }

    Duration backoff(int attempts) {
        return props.initialBackoff().multipliedBy(1L << Math.min(attempts - 1, 10));
    }
}
