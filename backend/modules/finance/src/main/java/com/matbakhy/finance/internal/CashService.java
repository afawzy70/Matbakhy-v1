package com.matbakhy.finance.internal;

import com.matbakhy.cooks.api.KitchenDirectory;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cash collection (BR-032): order completion and payment are independent. The cook declares what was collected for a
 * completed order; finance verifies or disputes it separately. A short or zero report is an exception (DISPUTED), never
 * a success. The reporter can never verify their own report.
 */
@Service
public class CashService {

    public enum Decision { VERIFY, DISPUTE }

    public record ReportInput(long amountMinor, String currency, Instant collectedAt, String method, String externalReference,
                              long expectedVersion) {
    }

    public record VerificationInput(UUID reportId, Decision decision, Long verifiedAmountMinor, String reference, String reason,
                                    long expectedVersion) {
    }

    public record Money(long amountMinor, String currency) {
    }

    public record CashReportView(UUID id, Money amount, Instant collectedAt, String externalReference, Instant reportedAt, Money discrepancy,
                                 String state, Money verifiedAmount, String verificationReference, Instant verifiedAt, long version) {
    }

    /** One order's cash position: what is due and, only if they exist, the cook's report and finance's verification. */
    public record CashItem(UUID orderId, String publicNumber, UUID kitchenId, String orderState, Money amountDue, String cashState,
                           CashReportView report) {
    }

    public record CashPage(List<CashItem> items, String nextCursor, boolean hasMore) {
    }

    private final JdbcTemplate jdbc;
    private final KitchenDirectory kitchens;
    private final IdempotentCommands idempotency;
    private final AuditLog audit;
    private final Clock clock;

    CashService(JdbcTemplate jdbc, KitchenDirectory kitchens, IdempotentCommands idempotency, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.kitchens = kitchens;
        this.idempotency = idempotency;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public CashReportView report(UUID cookId, String key, UUID orderId, ReportInput in) {
        UUID kitchen = kitchens.ownedBy(cookId).map(KitchenDirectory.KitchenStatus::kitchenId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "not a cook"));
        UUID reportId = idempotency.execute(cookId, "cash.report", key, Map.of("orderId", orderId.toString(), "input", in), UUID.class, () -> {
            Map<String, Object> o = jdbc.queryForList("""
                    SELECT o.state, o.version, o.total_minor, o.created_at, c.state AS cash_state FROM customer_order o
                    JOIN cash_obligation c ON c.order_id = o.id WHERE o.id = ? AND o.kitchen_id = ? FOR UPDATE OF c""", orderId, kitchen)
                    .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
            if (in.expectedVersion() != (Long) o.get("version")) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale order version");
            }
            if (!"COMPLETED".equals(o.get("state"))) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "cash is reported after handover", List.of(new FieldError("state", (String) o.get("state"))));
            }
            if (!"DUE".equals(o.get("cash_state"))) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "cash already reported", List.of(new FieldError("cashState", (String) o.get("cash_state"))));
            }
            if (!"EGP".equals(in.currency()) || !"CASH".equals(in.method())) {
                throw invalid("method", "CASH_EGP_ONLY");
            }
            Instant now = clock.instant();
            if (in.collectedAt() == null || in.collectedAt().isAfter(now) || in.collectedAt().isBefore(((Timestamp) o.get("created_at")).toInstant())) {
                throw invalid("collectedAt", "BETWEEN_ORDER_AND_NOW");
            }
            long due = (Long) o.get("total_minor");
            long discrepancy = Math.subtractExact(in.amountMinor(), due);
            UUID id = UUID.randomUUID();
            String reference = in.externalReference() == null || in.externalReference().isBlank() ? null : in.externalReference().strip();
            jdbc.update("""
                    INSERT INTO cash_report (id, order_id, reporter_id, amount_minor, collected_at, external_reference, reported_at, discrepancy_minor, state)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'REPORTED')""",
                    id, orderId, cookId, in.amountMinor(), Timestamp.from(in.collectedAt()), reference, Timestamp.from(now), discrepancy);
            // An exact report awaits verification; any difference is an exception for finance, not a success.
            jdbc.update("UPDATE cash_obligation SET state = ?, version = version + 1 WHERE order_id = ? AND state = 'DUE'",
                    discrepancy == 0 ? "REPORTED" : "DISPUTED", orderId);
            return id;
        });
        return view(reportId);
    }

    /** Refuses (and audits) a reporter verifying their own report; separate so the denial audit survives. */
    @Transactional(noRollbackFor = ApiException.class)
    public void refuseSelfVerification(UUID staffId, UUID reportId, String requestId) {
        UUID reporter = jdbc.query("SELECT reporter_id FROM cash_report WHERE id = ?", rs -> rs.next() ? rs.getObject(1, UUID.class) : null, reportId);
        if (staffId.equals(reporter)) {
            audit.record(new Entry(staffId, "cash.verify", "finance.verify", "cash_report", reportId.toString(), Outcome.DENIED,
                    "reporter cannot verify own report", requestId, Map.of()));
            throw new ApiException(ErrorCode.FORBIDDEN, "separation of duties");
        }
    }

    @Transactional
    public CashReportView verify(UUID staffId, String key, VerificationInput in, String requestId) {
        UUID reportId = idempotency.execute(staffId, "cash.verify", key, in, UUID.class, () -> {
            Map<String, Object> r = jdbc.queryForList("""
                    SELECT r.order_id, r.reporter_id, r.state, r.version, r.discrepancy_minor, o.total_minor FROM cash_report r
                    JOIN customer_order o ON o.id = r.order_id WHERE r.id = ? FOR UPDATE OF r""", in.reportId()).stream().findFirst()
                    .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "cash report not found"));
            if (staffId.equals(r.get("reporter_id"))) {
                throw new ApiException(ErrorCode.FORBIDDEN, "separation of duties");
            }
            if (in.expectedVersion() != (Long) r.get("version")) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale cash report version");
            }
            if (!"REPORTED".equals(r.get("state"))) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "report already decided", List.of(new FieldError("state", (String) r.get("state"))));
            }
            long due = (Long) r.get("total_minor");
            String reason = in.reason() == null ? null : in.reason().strip();
            if (in.verifiedAmountMinor() == null || in.verifiedAmountMinor() < 0) {
                throw invalid("verifiedAmountMinor", "REQUIRED");
            }
            boolean exceptional = (Long) r.get("discrepancy_minor") != 0 || in.decision() == Decision.DISPUTE;
            if (exceptional && (reason == null || reason.length() < 3)) {
                throw invalid("reason", "REQUIRED_FOR_EXCEPTIONS");
            }
            if (in.decision() == Decision.VERIFY && in.verifiedAmountMinor() != due) {
                // Verification confirms full collection; any other amount must be recorded as a dispute.
                throw invalid("verifiedAmountMinor", "MUST_EQUAL_AMOUNT_DUE_OR_DISPUTE");
            }
            String outcome = in.decision() == Decision.VERIFY ? "VERIFIED" : "DISPUTED";
            Instant now = clock.instant();
            jdbc.update("""
                    UPDATE cash_report SET state = ?, verified_by = ?, verified_amount_minor = ?, verification_reference = ?, verification_reason = ?,
                           verified_at = ?, version = version + 1 WHERE id = ?""",
                    outcome, staffId, in.verifiedAmountMinor(), in.reference() == null || in.reference().isBlank() ? null : in.reference().strip(),
                    reason, Timestamp.from(now), in.reportId());
            jdbc.update("UPDATE cash_obligation SET state = ?, version = version + 1 WHERE order_id = ?", outcome, r.get("order_id"));
            audit.record(new Entry(staffId, "cash.verify", "finance.verify", "cash_report", in.reportId().toString(), Outcome.ALLOWED,
                    reason == null ? outcome : reason, requestId, Map.of("decision", in.decision().name(), "verifiedAmountMinor", in.verifiedAmountMinor())));
            return in.reportId();
        });
        return view(reportId);
    }

    public CashPage list(String state, UUID kitchenId, String cursor, int limit) {
        if (state != null && !Set.of("DUE", "REPORTED", "VERIFIED", "DISPUTED", "NOT_DUE").contains(state)) {
            throw invalid("state", "UNKNOWN_STATE");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        Instant afterTime = Instant.EPOCH;
        UUID afterId = new UUID(0, 0);
        if (cursor != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                afterTime = Instant.parse(parts[0]);
                afterId = UUID.fromString(parts[1]);
            } catch (RuntimeException e) {
                throw invalid("cursor", "INVALID");
            }
        }
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT o.id, o.public_number, o.kitchen_id, o.state AS order_state, c.amount_minor, c.state AS cash_state, c.created_at,
                       r.id AS report_id
                FROM cash_obligation c JOIN customer_order o ON o.id = c.order_id LEFT JOIN cash_report r ON r.order_id = o.id
                WHERE (?::text IS NULL OR c.state = ?) AND (?::uuid IS NULL OR o.kitchen_id = ?) AND (c.created_at, o.id) > (?, ?)
                ORDER BY c.created_at, o.id LIMIT ?""",
                state, state, kitchenId, kitchenId, Timestamp.from(afterTime), afterId, limit + 1);
        boolean more = rows.size() > limit;
        List<Map<String, Object>> page = more ? rows.subList(0, limit) : rows;
        List<CashItem> items = page.stream().map(row -> new CashItem((UUID) row.get("id"), (String) row.get("public_number"),
                (UUID) row.get("kitchen_id"), (String) row.get("order_state"), egp((Long) row.get("amount_minor")), (String) row.get("cash_state"),
                row.get("report_id") == null ? null : view((UUID) row.get("report_id")))).toList();
        String next = null;
        if (more) {
            Map<String, Object> last = page.getLast();
            next = Base64.getUrlEncoder().withoutPadding().encodeToString((((Timestamp) last.get("created_at")).toInstant() + "|" + last.get("id"))
                    .getBytes(StandardCharsets.UTF_8));
        }
        return new CashPage(items, next, more);
    }

    private CashReportView view(UUID reportId) {
        return jdbc.query("SELECT * FROM cash_report WHERE id = ?", (rs, n) -> new CashReportView(rs.getObject("id", UUID.class),
                egp(rs.getLong("amount_minor")), rs.getTimestamp("collected_at").toInstant(), rs.getString("external_reference"),
                rs.getTimestamp("reported_at").toInstant(), egp(rs.getLong("discrepancy_minor")), rs.getString("state"),
                rs.getObject("verified_amount_minor") == null ? null : egp(rs.getLong("verified_amount_minor")), rs.getString("verification_reference"),
                rs.getTimestamp("verified_at") == null ? null : rs.getTimestamp("verified_at").toInstant(), rs.getLong("version")), reportId).getFirst();
    }

    private static Money egp(long minor) {
        return new Money(minor, "EGP");
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
