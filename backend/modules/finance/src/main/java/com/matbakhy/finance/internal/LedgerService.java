package com.matbakhy.finance.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commission ledger (BR-033). A completed order owes the platform its commission snapshot (rate taken at placement,
 * on the subtotal only, delivery excluded, rounded half up): one balanced, immutable group per completion event —
 * debit the cook's commission receivable, credit platform commission revenue. Delivering the event again posts
 * nothing (unique source event); later policy changes cannot alter it.
 */
@Service
public class LedgerService {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    LedgerService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Returns true when this call posted the group, false when the source event was already posted. */
    @Transactional
    public boolean postCommission(UUID sourceEventId, UUID orderId) {
        Map<String, Object> o = jdbc.queryForList("""
                SELECT o.kitchen_id, o.subtotal_minor, o.commission_bps_snapshot, o.commission_minor, o.state, e.to_state AS event_state
                FROM customer_order o JOIN order_event e ON e.order_id = o.id AND e.id = ? WHERE o.id = ?""", sourceEventId, orderId)
                .stream().findFirst().orElseThrow(() -> new IllegalArgumentException("unknown completion event " + sourceEventId));
        if (!"COMPLETED".equals(o.get("event_state"))) {
            throw new IllegalArgumentException("commission posts only for completion events");
        }
        UUID group = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO ledger_group (id, source_event_id, kind, order_id, kitchen_id, rate_bps, base_minor, posted_at)
                VALUES (?, ?, 'COMMISSION', ?, ?, ?, ?, ?) ON CONFLICT (kind, source_event_id) DO NOTHING""",
                group, sourceEventId, orderId, o.get("kitchen_id"), o.get("commission_bps_snapshot"), o.get("subtotal_minor"),
                Timestamp.from(clock.instant()));
        if (inserted == 0) {
            return false;
        }
        long amount = (Long) o.get("commission_minor");
        jdbc.update("INSERT INTO ledger_entry (id, group_id, account, direction, amount_minor) VALUES (?, ?, 'COOK_COMMISSION_RECEIVABLE', 'DEBIT', ?)",
                UUID.randomUUID(), group, amount);
        jdbc.update("INSERT INTO ledger_entry (id, group_id, account, direction, amount_minor) VALUES (?, ?, 'PLATFORM_COMMISSION_REVENUE', 'CREDIT', ?)",
                UUID.randomUUID(), group, amount);
        return true;
    }

    /** Completion events not yet posted (read model over order events). */
    public List<Map<String, Object>> unpostedCompletions(int limit) {
        return jdbc.queryForList("""
                SELECT e.id AS event_id, e.order_id FROM order_event e
                WHERE e.to_state = 'COMPLETED'
                  AND NOT EXISTS (SELECT 1 FROM ledger_group g WHERE g.kind = 'COMMISSION' AND g.source_event_id = e.id)
                ORDER BY e.occurred_at LIMIT ?""", limit);
    }
}
