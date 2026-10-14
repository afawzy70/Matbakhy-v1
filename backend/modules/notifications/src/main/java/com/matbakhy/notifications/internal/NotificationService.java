package com.matbakhy.notifications.internal;

import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import com.matbakhy.shared.idempotency.IdempotentCommands;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Inbox (API-031/032), device registrations (API-033/034) and integration operations (API-096/097). */
@Service
public class NotificationService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificationService.class);

    public record Notification(UUID id, String type, UUID orderId, Map<String, String> params, Instant createdAt, Instant readAt) {
    }

    public record NotificationPage(List<Notification> items, String nextCursor, boolean hasMore) {
    }

    public record DeviceRegistration(String installationId, String platform, String environment, String appVersion, Instant updatedAt) {
    }

    public record ChannelHealth(String channel, int pending, int dead, int sent) {
    }

    public record IntegrationHealth(int outboxPending, int outboxFailed, Instant oldestPendingAt, List<ChannelHealth> channels,
                                    List<Map<String, Object>> recentFailures) {
    }

    public record OutboxStatus(UUID eventId, String eventType, String status, int attempts, Instant availableAt, long version) {
    }

    private final JdbcTemplate jdbc;
    private final TokenVault vault;
    private final IdempotentCommands idempotency;
    private final AuditLog audit;
    private final JsonMapper json;
    private final Clock clock;

    NotificationService(JdbcTemplate jdbc, TokenVault vault, IdempotentCommands idempotency, AuditLog audit, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.vault = vault;
        this.idempotency = idempotency;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    // ---- inbox ----

    public NotificationPage inbox(UUID accountId, boolean unreadOnly, String cursor, int limit) {
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        Instant beforeTime = Instant.parse("9999-12-31T00:00:00Z");
        UUID beforeId = new UUID(-1L, -1L);
        if (cursor != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                beforeTime = Instant.parse(parts[0]);
                beforeId = UUID.fromString(parts[1]);
            } catch (RuntimeException e) {
                throw invalid("cursor", "INVALID");
            }
        }
        List<Notification> rows = jdbc.query("""
                SELECT id, type, order_id, params::text, created_at, read_at FROM notification
                WHERE recipient_id = ? AND (NOT ? OR read_at IS NULL) AND (created_at, id) < (?, ?)
                ORDER BY created_at DESC, id DESC LIMIT ?""",
                (rs, n) -> new Notification(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), params(rs.getString(4)),
                        rs.getTimestamp(5).toInstant(), rs.getTimestamp(6) == null ? null : rs.getTimestamp(6).toInstant()),
                accountId, unreadOnly, Timestamp.from(beforeTime), beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<Notification> page = more ? rows.subList(0, limit) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString((page.getLast().createdAt() + "|" + page.getLast().id())
                .getBytes(StandardCharsets.UTF_8)) : null;
        return new NotificationPage(page, next, more);
    }

    @Transactional
    public Notification markRead(UUID accountId, UUID notificationId) {
        int n = jdbc.update("UPDATE notification SET read_at = coalesce(read_at, ?) WHERE id = ? AND recipient_id = ?",
                Timestamp.from(clock.instant()), notificationId, accountId);
        if (n == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "notification not found");
        }
        return jdbc.query("SELECT id, type, order_id, params::text, created_at, read_at FROM notification WHERE id = ?",
                (rs, i) -> new Notification(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), params(rs.getString(4)),
                        rs.getTimestamp(5).toInstant(), rs.getTimestamp(6).toInstant()), notificationId).getFirst();
    }

    private Map<String, String> params(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        JsonNode node = json.readTree(raw);
        node.properties().forEach(e -> out.put(e.getKey(), e.getValue().asString()));
        return out;
    }

    // ---- devices ----

    @Transactional
    public DeviceRegistration register(UUID accountId, String installationId, String platform, String environment, String appVersion, String token) {
        if (installationId == null || !installationId.matches("[A-Za-z0-9-]{8,64}")) {
            throw invalid("installationId", "INVALID");
        }
        if (!Set.of("ANDROID", "IOS").contains(platform) || !Set.of("PRODUCTION", "DEVELOPMENT").contains(environment)) {
            throw invalid("platform", "ANDROID_OR_IOS");
        }
        if (token == null || token.length() < 16 || token.length() > 4096) {
            throw invalid("pushToken", "INVALID");
        }
        TokenVault.Sealed sealed = vault.seal(token);
        Instant now = clock.instant();
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, "device:" + installationId);
        // The same installation or token moving to this account (account switch) revokes the previous registration.
        jdbc.update("UPDATE device_registration SET revoked_at = ? WHERE revoked_at IS NULL AND (installation_id = ? OR token_hash = ?)",
                Timestamp.from(now), installationId, sealed.hash());
        jdbc.update("""
                INSERT INTO device_registration (id, user_id, installation_id, platform, environment, app_version, token_hash, token_ciphertext,
                                                 token_nonce, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), accountId, installationId, platform, environment, appVersion, sealed.hash(), sealed.ciphertext(), sealed.nonce(),
                Timestamp.from(now), Timestamp.from(now));
        return new DeviceRegistration(installationId, platform, environment, appVersion, now);
    }

    @Transactional
    public void unregister(UUID accountId, String installationId) {
        int n = jdbc.update("UPDATE device_registration SET revoked_at = ? WHERE installation_id = ? AND user_id = ? AND revoked_at IS NULL",
                Timestamp.from(clock.instant()), installationId, accountId);
        if (n == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "device not registered");
        }
    }

    // ---- integration operations ----

    public IntegrationHealth health() {
        Integer pending = jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE status = 'PENDING'", Integer.class);
        Integer failed = jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE status = 'FAILED'", Integer.class);
        Timestamp oldest = jdbc.queryForObject("SELECT min(created_at) FROM outbox_event WHERE status = 'PENDING'", Timestamp.class);
        List<ChannelHealth> channels = jdbc.query("""
                SELECT channel, count(*) FILTER (WHERE status = 'PENDING'), count(*) FILTER (WHERE status = 'DEAD'),
                       count(*) FILTER (WHERE status = 'SENT') FROM notification_delivery GROUP BY channel ORDER BY channel""",
                (rs, n) -> new ChannelHealth(rs.getString(1), rs.getInt(2), rs.getInt(3), rs.getInt(4)));
        // Redacted: ids, types, attempts and error class names only; no recipients, tokens or payloads.
        List<Map<String, Object>> failures = jdbc.queryForList("""
                SELECT d.id AS "deliveryId", d.channel, d.attempts, d.last_error AS "lastError", n.type FROM notification_delivery d
                JOIN notification n ON n.id = d.notification_id WHERE d.status = 'DEAD' ORDER BY d.next_attempt_at DESC LIMIT 20""");
        return new IntegrationHealth(pending, failed, oldest == null ? null : oldest.toInstant(), channels, failures);
    }

    @Transactional
    public OutboxStatus retryOutbox(UUID staffId, String key, UUID eventId, long expectedVersion, String reason, String requestId) {
        UUID id = idempotency.execute(staffId, "outbox.retry", key, Map.of("eventId", eventId.toString(), "expectedVersion", expectedVersion,
                "reason", String.valueOf(reason)), UUID.class, () -> {
            Map<String, Object> e = jdbc.queryForList("SELECT status, version FROM outbox_event WHERE id = ? FOR UPDATE", eventId).stream().findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "outbox event not found"));
            if (expectedVersion != (Long) e.get("version")) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale outbox version");
            }
            if (!"FAILED".equals(e.get("status"))) {
                // Only dead-lettered events are replayed; dispatch itself is idempotent per recipient and event.
                throw new ApiException(ErrorCode.STATE_CONFLICT, "only FAILED events can be retried", List.of(new FieldError("status", (String) e.get("status"))));
            }
            if (reason == null || reason.strip().length() < 3) {
                throw invalid("reason", "REQUIRED");
            }
            jdbc.update("""
                    UPDATE outbox_event SET status = 'PENDING', attempts = 0, available_at = ?, last_error = NULL, version = version + 1 WHERE id = ?""",
                    Timestamp.from(clock.instant()), eventId);
            audit.record(new Entry(staffId, "outbox.retry", "integration.ops", "outbox_event", eventId.toString(), Outcome.ALLOWED, reason.strip(),
                    requestId, Map.of()));
            // Runbook step 5: the operator finds the retry by request ID; the reason stays in the audit row only.
            log.info("outbox event {} reset for retry by staff {} (audit outbox.retry)", eventId, staffId);
            return eventId;
        });
        return jdbc.query("SELECT id, event_type, status, attempts, available_at, version FROM outbox_event WHERE id = ?",
                (rs, n) -> new OutboxStatus(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getTimestamp(5).toInstant(), rs.getLong(6)), id).getFirst();
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
