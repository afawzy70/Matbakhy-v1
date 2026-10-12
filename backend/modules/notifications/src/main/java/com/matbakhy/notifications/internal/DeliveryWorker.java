package com.matbakhy.notifications.internal;

import com.matbakhy.notifications.api.NotificationChannels.EmailSender;
import com.matbakhy.notifications.api.NotificationChannels.PushSender;
import com.matbakhy.notifications.api.NotificationChannels.Result;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import com.matbakhy.shared.web.RequestContext;
import java.util.regex.Pattern;
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
 * Delivers inbox notifications over channels with leases, bounded exponential retry and a visible dead letter.
 * Provider calls happen outside database transactions; results are recorded only by the worker holding the lease,
 * so two workers never both record a delivery. Provider failures never affect the inbox or the order.
 */
@Component
public class DeliveryWorker {

    private static final Logger LOG = LoggerFactory.getLogger(DeliveryWorker.class);
    /** Provider error text is kept only when it is a plain code; anything else could carry provider-echoed content. */
    private static final Pattern SAFE_ERROR = Pattern.compile("[A-Za-z0-9_.-]{1,80}");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final EmailSender email;
    private final PushSender push;
    private final TokenVault vault;
    private final NotificationProperties props;
    private final OutboxDispatcher dispatcher;
    private final JsonMapper json;
    private final Clock clock;

    DeliveryWorker(JdbcTemplate jdbc, PlatformTransactionManager txm, EmailSender email, PushSender push, TokenVault vault,
                   NotificationProperties props, OutboxDispatcher dispatcher, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(txm);
        this.email = email;
        this.push = push;
        this.vault = vault;
        this.props = props;
        this.dispatcher = dispatcher;
        this.json = json;
        this.clock = clock;
    }

    public int deliverOnce(String worker, int limit) {
        Instant now = clock.instant();
        List<Map<String, Object>> claimed = tx.execute(s -> jdbc.queryForList("""
                UPDATE notification_delivery SET lease_owner = ?, leased_until = ? WHERE id IN (
                    SELECT id FROM notification_delivery WHERE status = 'PENDING' AND next_attempt_at <= ? AND (leased_until IS NULL OR leased_until < ?)
                    ORDER BY next_attempt_at, id LIMIT ? FOR UPDATE SKIP LOCKED)
                RETURNING id, notification_id, channel, target, attempts""",
                worker, Timestamp.from(now.plus(props.lease())), Timestamp.from(now), Timestamp.from(now), limit));
        int sent = 0;
        for (Map<String, Object> d : claimed) {
            UUID id = (UUID) d.get("id");
            int attempts = ((Integer) d.get("attempts")) + 1;
            Result result;
            try {
                result = send(id, (UUID) d.get("notification_id"), (String) d.get("channel"), (String) d.get("target"));
            } catch (RuntimeException failure) {
                result = new Result(false, false, null, failure.getClass().getSimpleName());
            }
            if (record(id, worker, attempts, result)) {
                sent++;
            }
        }
        return sent;
    }

    private Result send(UUID deliveryId, UUID notificationId, String channel, String target) {
        Map<String, Object> n = jdbc.queryForMap("""
                SELECT n.recipient_id, n.type, n.params::text AS params, u.locale, u.status FROM notification n JOIN app_user u ON u.id = n.recipient_id
                WHERE n.id = ?""", notificationId);
        if (!"ACTIVE".equals(n.get("status"))) {
            return Result.permanentFailure("RECIPIENT_NOT_ACTIVE");
        }
        Map<String, String> params = new HashMap<>();
        JsonNode p = json.readTree((String) n.get("params"));
        p.properties().forEach(e -> params.put(e.getKey(), e.getValue().asString()));
        String template = "notification." + ((String) n.get("type")).toLowerCase();
        if ("EMAIL".equals(channel)) {
            return email.send(deliveryId, (UUID) n.get("recipient_id"), (String) n.get("locale"), template, params);
        }
        Map<String, Object> device = jdbc.queryForList("""
                SELECT platform, token_ciphertext, token_nonce FROM device_registration
                WHERE installation_id = ? AND user_id = ? AND revoked_at IS NULL""", target, n.get("recipient_id")).stream().findFirst().orElse(null);
        if (device == null) {
            return Result.permanentFailure("DEVICE_UNREGISTERED");
        }
        String token = vault.open((byte[]) device.get("token_ciphertext"), (byte[]) device.get("token_nonce"));
        return push.send(deliveryId, (String) device.get("platform"), token, template, params);
    }

    private boolean record(UUID id, String worker, int attempts, Result result) {
        Instant now = clock.instant();
        if (result.delivered()) {
            return Boolean.TRUE.equals(tx.execute(s -> jdbc.update("""
                    UPDATE notification_delivery SET status = 'SENT', attempts = ?, sent_at = ?, provider_ref = ?, last_error = NULL,
                           lease_owner = NULL, leased_until = NULL WHERE id = ? AND lease_owner = ? AND status = 'PENDING'""",
                    attempts, Timestamp.from(now), result.providerRef(), id, worker) == 1));
        }
        boolean dead = result.permanent() || attempts >= props.maxAttempts();
        String status = result.permanent() && result.error() != null && result.error().startsWith("RECIPIENT_NOT_ACTIVE") ? "SKIPPED"
                : dead ? "DEAD" : "PENDING";
        tx.executeWithoutResult(s -> jdbc.update("""
                UPDATE notification_delivery SET status = ?, attempts = ?, next_attempt_at = ?, last_error = ?, lease_owner = NULL, leased_until = NULL
                WHERE id = ? AND lease_owner = ? AND status = 'PENDING'""",
                status, attempts, Timestamp.from(now.plus(dispatcher.backoff(attempts))), safeError(result.error()), id, worker));
        logFailure(id, attempts, status, safeError(result.error()));
        return false;
    }

    /** Logs the failure under the request ID that caused the notification, so one ID traces request, order and provider. */
    private void logFailure(UUID id, int attempts, String status, String error) {
        String requestId = jdbc.queryForList("""
                SELECT e.request_id FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                JOIN outbox_event e ON e.id = n.source_event_id WHERE d.id = ?""", String.class, id).stream().findFirst().orElse(null);
        try (var traced = MDC.putCloseable(RequestContext.REQUEST_ID_MDC_KEY, requestId)) {
            LOG.warn("notification delivery {} failed on attempt {}: {} (now {})", id, attempts, error, status);
        }
    }

    /** Stored and logged provider errors are plain codes; free text (which may echo a recipient or token) is replaced. */
    private static String safeError(String error) {
        return error == null ? null : SAFE_ERROR.matcher(error).matches() ? error : "PROVIDER_ERROR";
    }
}
