package com.matbakhy.finance.internal;

import com.matbakhy.finance.internal.CashService.Money;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import com.matbakhy.shared.idempotency.IdempotentCommands;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finance operations (BR-034..036). Cash is collected by the cook, so the platform's claim is the commission receivable,
 * never the gross order amount. A refund is a full refund of the verified collection less refunds already committed
 * (anything not rejected), so approved-but-unpaid refunds block over-refunds. A verified refund reverses the order's
 * commission once (one reversal group per refund; the full commission for a full refund is the proposed synthetic
 * policy until O04). Settlement batches collect ledger groups once each; reviewed batches are immutable; payment is
 * recorded only with an external reference and reconciled by another person. Duties are separated by database checks.
 */
@Service
public class FinanceOpsService {

    public record Refund(UUID id, UUID orderId, UUID kitchenId, String caseReference, Money amount, String state, String reason,
                         UUID requestedBy, UUID approvedBy, UUID paidBy, String paymentReference, UUID verifiedBy, String decisionReason,
                         Instant createdAt, Instant updatedAt, long version) {
    }

    public record SettlementLine(UUID ledgerGroupId, String kind, UUID orderId, Money amount) {
    }

    public record Settlement(UUID id, UUID kitchenId, LocalDate periodFrom, LocalDate periodTo, String state, Money total, String direction,
                             List<SettlementLine> lines, UUID createdBy, UUID reviewedBy, String paymentReference, UUID paymentRecordedBy,
                             UUID reconciledBy, Instant createdAt, Instant updatedAt, long version) {
    }

    private final JdbcTemplate jdbc;
    private final IdempotentCommands idempotency;
    private final AuditLog audit;
    private final Clock clock;

    FinanceOpsService(JdbcTemplate jdbc, IdempotentCommands idempotency, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    // ---- refunds (API-084/085) ----

    @Transactional
    public Refund requestRefund(UUID staffId, String key, UUID orderId, String caseReference, Long fullAmountMinor, String reason, String requestId) {
        UUID id = idempotency.execute(staffId, "refund.request", key, Map.of("orderId", String.valueOf(orderId), "case", String.valueOf(caseReference),
                "amount", String.valueOf(fullAmountMinor), "reason", String.valueOf(reason)), UUID.class, () -> {
            Map<String, Object> o = jdbc.queryForList("""
                    SELECT o.kitchen_id, c.state AS cash_state, r.verified_amount_minor FROM customer_order o
                    JOIN cash_obligation c ON c.order_id = o.id LEFT JOIN cash_report r ON r.order_id = o.id
                    WHERE o.id = ? FOR UPDATE OF c""", orderId).stream().findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
            if (!"VERIFIED".equals(o.get("cash_state")) || o.get("verified_amount_minor") == null) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "no verified collection to refund", List.of(new FieldError("orderId", "COLLECTION_NOT_VERIFIED")));
            }
            long committed = jdbc.queryForObject("SELECT coalesce(sum(amount_minor), 0) FROM refund WHERE order_id = ? AND state <> 'REJECTED'",
                    Long.class, orderId);
            long refundable = ((Number) o.get("verified_amount_minor")).longValue() - committed;
            if (refundable <= 0) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "already refunded", List.of(new FieldError("fullAmountMinor", "OVER_REFUND")));
            }
            if (fullAmountMinor == null || fullAmountMinor != refundable) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "full refund of " + refundable + " required",
                        List.of(new FieldError("fullAmountMinor", fullAmountMinor != null && fullAmountMinor > refundable ? "OVER_REFUND" : "FULL_REFUND_REQUIRED")));
            }
            String why = text("reason", reason, 3, 1000);
            String ref = text("caseId", caseReference, 3, 100);
            UUID refund = UUID.randomUUID();
            Instant now = clock.instant();
            jdbc.update("""
                    INSERT INTO refund (id, order_id, kitchen_id, case_reference, amount_minor, state, reason, requested_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, 'REQUESTED', ?, ?, ?, ?)""", refund, orderId, o.get("kitchen_id"), ref, fullAmountMinor, why, staffId,
                    Timestamp.from(now), Timestamp.from(now));
            audit.record(new Entry(staffId, "refund.request", Permission.FINANCE_REFUND_APPROVE.code(), "refund", refund.toString(), Outcome.ALLOWED, why,
                    requestId, Map.of("orderId", orderId.toString(), "amountMinor", fullAmountMinor)));
            return refund;
        });
        return refund(id);
    }

    @Transactional
    public Refund refundAction(UUID staffId, String key, UUID refundId, String action, Long expectedVersion, String evidenceRef, String reason,
                               String requestId) {
        UUID id = idempotency.execute(staffId, "refund.action", key, Map.of("refundId", refundId.toString(), "action", String.valueOf(action),
                "expectedVersion", String.valueOf(expectedVersion), "evidence", String.valueOf(evidenceRef)), UUID.class, () -> {
            Refund r = jdbc.query("SELECT * FROM refund WHERE id = ? FOR UPDATE", FinanceOpsService::mapRefund, refundId).stream().findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "refund not found"));
            if (expectedVersion == null || expectedVersion != r.version()) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale refund version");
            }
            Instant now = clock.instant();
            switch (String.valueOf(action)) {
                case "APPROVE" -> {
                    requireState(r.state(), "REQUESTED");
                    separate(staffId, r.requestedBy());
                    jdbc.update("UPDATE refund SET state = 'APPROVED', approved_by = ?, updated_at = ?, version = version + 1 WHERE id = ?", staffId,
                            Timestamp.from(now), refundId);
                }
                case "REJECT" -> {
                    if (!Set.of("REQUESTED", "APPROVED").contains(r.state())) {
                        throw conflict(r.state());
                    }
                    jdbc.update("UPDATE refund SET state = 'REJECTED', decision_reason = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                            text("reason", reason, 3, 1000), Timestamp.from(now), refundId);
                }
                case "PAYMENT_RECORDED" -> {
                    requireState(r.state(), "APPROVED");
                    jdbc.update("UPDATE refund SET state = 'PAID', paid_by = ?, payment_reference = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                            staffId, text("evidenceRef", evidenceRef, 3, 200), Timestamp.from(now), refundId);
                }
                case "VERIFY" -> {
                    requireState(r.state(), "PAID");
                    separate(staffId, r.paidBy());
                    reverseCommission(refundId, r.orderId());
                    jdbc.update("UPDATE refund SET state = 'VERIFIED', verified_by = ?, updated_at = ?, version = version + 1 WHERE id = ?", staffId,
                            Timestamp.from(now), refundId);
                }
                default -> throw invalid("action", "APPROVE|REJECT|PAYMENT_RECORDED|VERIFY");
            }
            audit.record(new Entry(staffId, "refund." + action.toLowerCase(), Permission.FINANCE_REFUND_APPROVE.code(), "refund", refundId.toString(),
                    Outcome.ALLOWED, reason, requestId, Map.of("from", r.state())));
            return refundId;
        });
        return refund(id);
    }

    /** One reversal group per refund (unique source); the order's posted commission must exist. */
    private void reverseCommission(UUID refundId, UUID orderId) {
        Map<String, Object> g = jdbc.queryForList("""
                SELECT g.kitchen_id, g.rate_bps, g.base_minor, e.amount_minor FROM ledger_group g
                JOIN ledger_entry e ON e.group_id = g.id AND e.account = 'COOK_COMMISSION_RECEIVABLE' AND e.direction = 'DEBIT'
                WHERE g.order_id = ? AND g.kind = 'COMMISSION'""", orderId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.STATE_CONFLICT, "commission not posted yet", List.of(new FieldError("orderId", "COMMISSION_NOT_POSTED"))));
        UUID group = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO ledger_group (id, source_event_id, kind, order_id, kitchen_id, rate_bps, base_minor, posted_at)
                VALUES (?, ?, 'COMMISSION_REVERSAL', ?, ?, ?, ?, ?) ON CONFLICT (kind, source_event_id) DO NOTHING""",
                group, refundId, orderId, g.get("kitchen_id"), g.get("rate_bps"), g.get("base_minor"), Timestamp.from(clock.instant()));
        if (inserted == 0) {
            return;
        }
        long amount = ((Number) g.get("amount_minor")).longValue();
        jdbc.update("INSERT INTO ledger_entry (id, group_id, account, direction, amount_minor) VALUES (?, ?, 'PLATFORM_COMMISSION_REVENUE', 'DEBIT', ?)",
                UUID.randomUUID(), group, amount);
        jdbc.update("INSERT INTO ledger_entry (id, group_id, account, direction, amount_minor) VALUES (?, ?, 'COOK_COMMISSION_RECEIVABLE', 'CREDIT', ?)",
                UUID.randomUUID(), group, amount);
    }

    public Refund refund(UUID id) {
        return jdbc.query("SELECT * FROM refund WHERE id = ?", FinanceOpsService::mapRefund, id).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "refund not found"));
    }

    public List<Refund> refunds(String state, UUID orderId) {
        return jdbc.query("SELECT * FROM refund WHERE (?::text IS NULL OR state = ?) AND (?::uuid IS NULL OR order_id = ?) ORDER BY created_at DESC, id LIMIT 200",
                FinanceOpsService::mapRefund, state, state, orderId, orderId);
    }

    // ---- settlements (API-087/088) ----

    @Transactional
    public Settlement createSettlement(UUID staffId, String key, UUID kitchenId, LocalDate from, LocalDate to, List<UUID> entryIds, String requestId) {
        UUID id = idempotency.execute(staffId, "settlement.create", key, Map.of("kitchenId", String.valueOf(kitchenId), "from", String.valueOf(from),
                "to", String.valueOf(to), "entries", String.valueOf(entryIds)), UUID.class, () -> {
            if (kitchenId == null || from == null || to == null || to.isBefore(from)) {
                throw invalid("period", "VALID_PERIOD_REQUIRED");
            }
            if (entryIds == null || entryIds.isEmpty() || entryIds.size() > 500) {
                throw invalid("entryIds", "SIZE_1_500");
            }
            Set<UUID> unique = new LinkedHashSet<>(entryIds);
            UUID batch = UUID.randomUUID();
            Instant now = clock.instant();
            jdbc.update("""
                    INSERT INTO settlement_batch (id, kitchen_id, period_from, period_to, state, total_minor, created_by, created_at, updated_at)
                    VALUES (?, ?, ?, ?, 'DRAFT', 0, ?, ?, ?)""", batch, kitchenId, from, to, staffId, Timestamp.from(now), Timestamp.from(now));
            long total = 0;
            for (UUID entry : unique) {
                Map<String, Object> g = jdbc.queryForList("""
                        SELECT g.kind, g.kitchen_id, g.posted_at, e.amount_minor FROM ledger_group g
                        JOIN ledger_entry e ON e.group_id = g.id AND e.account = 'COOK_COMMISSION_RECEIVABLE'
                        WHERE g.id = ?""", entry).stream().findFirst()
                        .orElseThrow(() -> invalid("entryIds", "UNKNOWN_ENTRY"));
                if (!kitchenId.equals(g.get("kitchen_id"))) {
                    throw invalid("entryIds", "OTHER_KITCHEN");
                }
                LocalDate posted = LocalDate.ofInstant(((Timestamp) g.get("posted_at")).toInstant(), StatementService.CAIRO);
                if (posted.isBefore(from) || posted.isAfter(to)) {
                    throw invalid("entryIds", "OUTSIDE_PERIOD");
                }
                long amount = ((Number) g.get("amount_minor")).longValue() * ("COMMISSION".equals(g.get("kind")) ? 1 : -1);
                if (amount == 0) {
                    // MB-050 (D10): a zero-commission group has nothing to settle.
                    throw invalid("entryIds", "ZERO_AMOUNT");
                }
                try {
                    jdbc.update("INSERT INTO settlement_line (batch_id, ledger_group_id, amount_minor) VALUES (?, ?, ?)", batch, entry, amount);
                } catch (DuplicateKeyException e) {
                    throw new ApiException(ErrorCode.STATE_CONFLICT, "entry already in a settlement", List.of(new FieldError("entryIds", "ALREADY_SETTLED")));
                }
                total += amount;
            }
            jdbc.update("UPDATE settlement_batch SET total_minor = ? WHERE id = ?", total, batch);
            audit.record(new Entry(staffId, "settlement.create", Permission.FINANCE_SETTLEMENT.code(), "settlement_batch", batch.toString(),
                    Outcome.ALLOWED, null, requestId, Map.of("kitchenId", kitchenId.toString(), "totalMinor", total)));
            return batch;
        });
        return settlement(id);
    }

    @Transactional
    public Settlement settlementAction(UUID staffId, String key, UUID batchId, String action, Long expectedVersion, String evidenceRef, String requestId) {
        UUID id = idempotency.execute(staffId, "settlement.action", key, Map.of("batchId", batchId.toString(), "action", String.valueOf(action),
                "expectedVersion", String.valueOf(expectedVersion), "evidence", String.valueOf(evidenceRef)), UUID.class, () -> {
            Map<String, Object> b = jdbc.queryForList("SELECT state, version, created_by, payment_recorded_by FROM settlement_batch WHERE id = ? FOR UPDATE",
                    batchId).stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "settlement not found"));
            if (expectedVersion == null || expectedVersion != (Long) b.get("version")) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale settlement version");
            }
            String state = (String) b.get("state");
            Instant now = clock.instant();
            switch (String.valueOf(action)) {
                case "REVIEW" -> {
                    requireState(state, "DRAFT");
                    separate(staffId, (UUID) b.get("created_by"));
                    jdbc.update("UPDATE settlement_batch SET state = 'REVIEWED', reviewed_by = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                            staffId, Timestamp.from(now), batchId);
                }
                case "PAYMENT_RECORDED" -> {
                    requireState(state, "REVIEWED");
                    jdbc.update("""
                            UPDATE settlement_batch SET state = 'PAYMENT_RECORDED', payment_reference = ?, payment_recorded_by = ?, updated_at = ?,
                                   version = version + 1 WHERE id = ?""", text("evidenceRef", evidenceRef, 3, 200), staffId, Timestamp.from(now), batchId);
                }
                case "RECONCILE" -> {
                    requireState(state, "PAYMENT_RECORDED");
                    separate(staffId, (UUID) b.get("payment_recorded_by"));
                    jdbc.update("UPDATE settlement_batch SET state = 'RECONCILED', reconciled_by = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                            staffId, Timestamp.from(now), batchId);
                }
                case "CANCEL" -> {
                    // Reviewed batches are immutable; corrections are new batches with reversal entries.
                    requireState(state, "DRAFT");
                    jdbc.update("UPDATE settlement_batch SET state = 'CANCELLED', updated_at = ?, version = version + 1 WHERE id = ?",
                            Timestamp.from(now), batchId);
                    jdbc.update("UPDATE settlement_line SET active = false WHERE batch_id = ?", batchId);
                }
                default -> throw invalid("action", "REVIEW|PAYMENT_RECORDED|RECONCILE|CANCEL");
            }
            audit.record(new Entry(staffId, "settlement." + action.toLowerCase(), Permission.FINANCE_SETTLEMENT.code(), "settlement_batch", batchId.toString(),
                    Outcome.ALLOWED, evidenceRef, requestId, Map.of("from", state)));
            return batchId;
        });
        return settlement(id);
    }

    public Settlement settlement(UUID id) {
        List<SettlementLine> lines = jdbc.query("""
                SELECT l.ledger_group_id, g.kind, g.order_id, l.amount_minor FROM settlement_line l JOIN ledger_group g ON g.id = l.ledger_group_id
                WHERE l.batch_id = ? ORDER BY g.posted_at, g.id""",
                (rs, n) -> new SettlementLine(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), egp(rs.getLong(4))), id);
        return jdbc.query("SELECT * FROM settlement_batch WHERE id = ?", (rs, n) -> new Settlement(rs.getObject("id", UUID.class),
                rs.getObject("kitchen_id", UUID.class), rs.getObject("period_from", LocalDate.class), rs.getObject("period_to", LocalDate.class),
                rs.getString("state"), egp(rs.getLong("total_minor")), "COOK_PAYS_PLATFORM", lines, rs.getObject("created_by", UUID.class),
                rs.getObject("reviewed_by", UUID.class), rs.getString("payment_reference"), rs.getObject("payment_recorded_by", UUID.class),
                rs.getObject("reconciled_by", UUID.class), rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(),
                rs.getLong("version")), id).stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "settlement not found"));
    }

    /** Ledger groups of a kitchen with a non-zero amount, not in an active settlement line, for building a batch. */
    public List<SettlementLine> unsettled(UUID kitchenId) {
        return jdbc.query("""
                SELECT g.id, g.kind, g.order_id, CASE WHEN g.kind = 'COMMISSION' THEN e.amount_minor ELSE -e.amount_minor END
                FROM ledger_group g JOIN ledger_entry e ON e.group_id = g.id AND e.account = 'COOK_COMMISSION_RECEIVABLE'
                WHERE g.kitchen_id = ? AND e.amount_minor <> 0
                  AND NOT EXISTS (SELECT 1 FROM settlement_line l WHERE l.ledger_group_id = g.id AND l.active)
                ORDER BY g.posted_at, g.id LIMIT 500""",
                (rs, n) -> new SettlementLine(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), egp(rs.getLong(4))), kitchenId);
    }

    // ---- helpers ----

    /** Separation of duties (also enforced by database checks). */
    private static void separate(UUID actor, UUID other) {
        if (actor.equals(other)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "separation of duties: the same person cannot do both steps");
        }
    }

    private static void requireState(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw conflict(actual);
        }
    }

    private static ApiException conflict(String state) {
        return new ApiException(ErrorCode.STATE_CONFLICT, "not allowed from " + state, List.of(new FieldError("state", state)));
    }

    private static Refund mapRefund(ResultSet rs, int n) throws SQLException {
        return new Refund(rs.getObject("id", UUID.class), rs.getObject("order_id", UUID.class), rs.getObject("kitchen_id", UUID.class),
                rs.getString("case_reference"), egp(rs.getLong("amount_minor")), rs.getString("state"), rs.getString("reason"),
                rs.getObject("requested_by", UUID.class), rs.getObject("approved_by", UUID.class), rs.getObject("paid_by", UUID.class),
                rs.getString("payment_reference"), rs.getObject("verified_by", UUID.class), rs.getString("decision_reason"),
                rs.getTimestamp("created_at").toInstant(), rs.getTimestamp("updated_at").toInstant(), rs.getLong("version"));
    }

    private static Money egp(long minor) {
        return new Money(minor, "EGP");
    }

    private static String text(String field, String value, int min, int max) {
        String v = value == null ? "" : value.strip();
        if (v.length() < min || v.length() > max) {
            throw invalid(field, "LENGTH_" + min + "_" + max);
        }
        return v;
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
