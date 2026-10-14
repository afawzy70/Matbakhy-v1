package com.matbakhy.operations.internal;

import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.Redaction;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcAuditLog implements AuditLog {

    private final JdbcTemplate jdbc;

    JdbcAuditLog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void record(Entry e) {
        Map<String, Object> details = Redaction.details(e.details());
        jdbc.update("""
                INSERT INTO audit_event (id, actor_id, action, permission, object_type, object_id, outcome, reason, request_id, details)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)""",
                UUID.randomUUID(), e.actorId(), e.action(), e.permission(), e.objectType(), e.objectId(), e.outcome().name(),
                Redaction.text(e.reason()), e.requestId(), json(details));
    }

    /** Minimal JSON for flat redacted maps (string, number, boolean, null values). */
    static String json(Map<String, Object> map) {
        return map.entrySet().stream().map(en -> quote(en.getKey()) + ":" + value(en.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
    }

    private static String value(Object v) {
        if (v == null) {
            return "null";
        }
        if (v instanceof Number || v instanceof Boolean) {
            return v.toString();
        }
        return quote(v.toString());
    }

    private static String quote(String s) {
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) {
                        b.append(String.format("\\u%04x", (int) c));
                    } else {
                        b.append(c);
                    }
                }
            }
        }
        return b.append('"').toString();
    }
}
