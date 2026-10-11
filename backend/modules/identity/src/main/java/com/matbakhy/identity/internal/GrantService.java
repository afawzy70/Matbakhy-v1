package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.GrantDirectory;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Staff grant lifecycle (BR-007): server-side only, no self-escalation, last-administrator protection, audited. */
@Service
public class GrantService implements GrantDirectory {

    public record Grant(UUID id, UUID userId, String permission, Instant grantedAt, Instant expiresAt, Instant revokedAt) {
    }

    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final Clock clock;

    GrantService(JdbcTemplate jdbc, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Set<Permission> activePermissions(UUID accountId) {
        Set<Permission> out = EnumSet.noneOf(Permission.class);
        jdbc.query("""
                SELECT permission FROM user_grant WHERE user_id = ? AND revoked_at IS NULL AND (expires_at IS NULL OR expires_at > ?)""",
                rs -> {
                    Permission.fromCode(rs.getString(1)).ifPresent(out::add);
                }, accountId, Timestamp.from(clock.instant()));
        return out;
    }

    @Override
    @Transactional
    public void bootstrapAdministratorIfNone(UUID accountId) {
        jdbc.execute("LOCK TABLE user_grant IN SHARE ROW EXCLUSIVE MODE");
        if (activeHolders(Permission.STAFF_GRANT) > 0) {
            return;
        }
        for (Permission p : List.of(Permission.STAFF_GRANT, Permission.AUDIT_READ)) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'bootstrap administrator') "
                    + "ON CONFLICT DO NOTHING", UUID.randomUUID(), accountId, p.code());
        }
        audit.record(new Entry(accountId, "staff.bootstrap", Permission.STAFF_GRANT.code(), "user", accountId.toString(),
                Outcome.ALLOWED, "bootstrap administrator configured by environment", null, Map.of()));
    }

    // Denials are audited before throwing; they must not be rolled back (no other writes precede them).
    @Transactional(noRollbackFor = ApiException.class)
    public Grant grant(UUID actorId, UUID targetId, String permissionCode, Instant expiresAt, String reason, String requestId) {
        Permission permission = Permission.fromCode(permissionCode).orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                "unknown permission", List.of(new ApiException.FieldError("permission", "UNKNOWN_PERMISSION"))));
        if (actorId.equals(targetId)) {
            audit.record(new Entry(actorId, "staff.grant", permission.code(), "user", targetId.toString(), Outcome.DENIED,
                    "self-escalation refused", requestId, Map.of()));
            throw new ApiException(ErrorCode.FORBIDDEN, "self escalation");
        }
        String status = jdbc.query("SELECT status FROM app_user WHERE id = ? FOR UPDATE", rs -> rs.next() ? rs.getString(1) : null, targetId);
        if (status == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "grant target missing");
        }
        if (!"ACTIVE".equals(status)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "grant target not active");
        }
        if (expiresAt != null && !expiresAt.isAfter(clock.instant())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "expiry in past", List.of(new ApiException.FieldError("expiresAt", "FUTURE")));
        }
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        int inserted = jdbc.update("""
                INSERT INTO user_grant (id, user_id, permission, granted_by, grant_reason, granted_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING""",
                id, targetId, permission.code(), actorId, reason, Timestamp.from(now), expiresAt == null ? null : Timestamp.from(expiresAt));
        if (inserted == 0) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "grant already active");
        }
        audit.record(new Entry(actorId, "staff.grant", permission.code(), "user", targetId.toString(), Outcome.ALLOWED, reason,
                requestId, Map.of("grantId", id.toString(), "expiresAt", expiresAt == null ? "never" : expiresAt.toString())));
        return new Grant(id, targetId, permission.code(), now, expiresAt, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public Grant revoke(UUID actorId, UUID targetId, UUID grantId, String reason, String requestId) {
        jdbc.execute("LOCK TABLE user_grant IN SHARE ROW EXCLUSIVE MODE");
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT permission, granted_at, expires_at FROM user_grant WHERE id = ? AND user_id = ? AND revoked_at IS NULL",
                grantId, targetId);
        if (rows.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "active grant not found");
        }
        String permission = (String) rows.getFirst().get("permission");
        if (Permission.STAFF_GRANT.code().equals(permission) && activeHolders(Permission.STAFF_GRANT) <= 1) {
            audit.record(new Entry(actorId, "staff.revoke", permission, "user", targetId.toString(), Outcome.DENIED,
                    "last administrator protected", requestId, Map.of("grantId", grantId.toString())));
            throw new ApiException(ErrorCode.STATE_CONFLICT, "last administrator");
        }
        Instant now = clock.instant();
        jdbc.update("UPDATE user_grant SET revoked_at = ?, revoked_by = ?, revoke_reason = ? WHERE id = ?",
                Timestamp.from(now), actorId, reason, grantId);
        audit.record(new Entry(actorId, "staff.revoke", permission, "user", targetId.toString(), Outcome.ALLOWED, reason, requestId,
                Map.of("grantId", grantId.toString())));
        Timestamp expires = (Timestamp) rows.getFirst().get("expires_at");
        return new Grant(grantId, targetId, permission, ((Timestamp) rows.getFirst().get("granted_at")).toInstant(),
                expires == null ? null : expires.toInstant(), now);
    }

    public List<Map<String, Object>> staff() {
        return jdbc.queryForList("""
                SELECT u.id AS user_id, u.display_name, u.status, g.id AS grant_id, g.permission, g.granted_at, g.expires_at
                FROM user_grant g JOIN app_user u ON u.id = g.user_id
                WHERE g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at > ?)
                ORDER BY u.id, g.permission""", Timestamp.from(clock.instant()));
    }

    private int activeHolders(Permission permission) {
        Integer n = jdbc.queryForObject("""
                SELECT count(DISTINCT g.user_id) FROM user_grant g JOIN app_user u ON u.id = g.user_id
                WHERE g.permission = ? AND g.revoked_at IS NULL AND (g.expires_at IS NULL OR g.expires_at > ?) AND u.status = 'ACTIVE'""",
                Integer.class, permission.code(), Timestamp.from(clock.instant()));
        return n == null ? 0 : n;
    }
}
