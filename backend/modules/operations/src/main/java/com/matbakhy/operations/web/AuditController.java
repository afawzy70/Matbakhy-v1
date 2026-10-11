package com.matbakhy.operations.web;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-094: redacted audit trail, newest first, keyset pagination. Requires audit.read. */
@RestController
class AuditController {

    record AuditEvent(UUID id, Instant occurredAt, UUID actorId, String action, String permission, String objectType,
                      String objectId, String outcome, String reason, String requestId, String details) {
    }

    record AuditPage(List<AuditEvent> items, String nextCursor, boolean hasMore) {
    }

    private final JdbcTemplate jdbc;

    AuditController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/api/v1/ops/audit")
    @PreAuthorize("hasAuthority('PERM_audit.read')")
    AuditPage audit(@RequestParam(required = false) UUID actorId, @RequestParam(required = false) String objectType,
                    @RequestParam(required = false) String objectId, @RequestParam(required = false) String action,
                    @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        int size = Math.max(1, Math.min(limit, 100));
        StringBuilder sql = new StringBuilder("SELECT id, occurred_at, actor_id, action, permission, object_type, object_id, outcome, "
                + "reason, request_id, details::text AS details FROM audit_event WHERE true");
        List<Object> args = new ArrayList<>();
        if (actorId != null) { sql.append(" AND actor_id = ?"); args.add(actorId); }
        if (objectType != null) { sql.append(" AND object_type = ?"); args.add(objectType); }
        if (objectId != null) { sql.append(" AND object_id = ?"); args.add(objectId); }
        if (action != null) { sql.append(" AND action = ?"); args.add(action); }
        if (cursor != null) {
            String[] parts = new String(Base64.getUrlDecoder().decode(cursor)).split("\\|", 2);
            sql.append(" AND (occurred_at, id) < (?, ?)");
            args.add(Timestamp.from(Instant.parse(parts[0])));
            args.add(UUID.fromString(parts[1]));
        }
        sql.append(" ORDER BY occurred_at DESC, id DESC LIMIT ?");
        args.add(size + 1);
        List<AuditEvent> rows = jdbc.query(sql.toString(), (rs, i) -> new AuditEvent(rs.getObject("id", UUID.class),
                rs.getTimestamp("occurred_at").toInstant(), rs.getObject("actor_id", UUID.class), rs.getString("action"),
                rs.getString("permission"), rs.getString("object_type"), rs.getString("object_id"), rs.getString("outcome"),
                rs.getString("reason"), rs.getString("request_id"), rs.getString("details")), args.toArray());
        boolean more = rows.size() > size;
        List<AuditEvent> page = more ? rows.subList(0, size) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                (page.getLast().occurredAt() + "|" + page.getLast().id()).getBytes()) : null;
        return new AuditPage(page, next, more);
    }

}
