package com.matbakhy.finance.internal;

import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.finance.internal.CashService.Money;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * API-064: the cook's own statement for a Cairo date range: completed orders with their cash position and the posted
 * commission receivable, refunds, commission reversals and settlements (MB-025). Cash the cook collected is the cook's
 * money; only the commission is owed to the platform ({@code direction} COOK_PAYS_PLATFORM). Outstanding commission is
 * posted minus reversed minus reconciled settlements. Staff read any kitchen's statement (API-086).
 */
@Service
public class StatementService {

    static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    public record Line(UUID orderId, String publicNumber, Instant completedAt, Money orderTotal, String cashState, Money commission,
                       String commissionState) {
    }

    public record Summary(int completedOrders, Money ordersTotal, Money cashReported, Money cashVerified, Money cashOutstanding,
                          Money commissionPosted, int commissionPending, Money commissionReversed, Money commissionSettled, Money commissionOutstanding) {
    }

    public record RefundSummary(UUID id, UUID orderId, Money amount, String state, String paymentReference, long version) {
    }

    public record SettlementSummary(UUID id, LocalDate periodFrom, LocalDate periodTo, String state, Money total, String paymentReference,
                                    long version) {
    }

    public record UnsettledEntry(UUID ledgerGroupId, String kind, UUID orderId, Money amount) {
    }

    public record StatementPage(UUID kitchenId, LocalDate from, LocalDate to, String direction, List<String> includes, Summary summary, List<Line> items,
                                List<RefundSummary> refunds, List<SettlementSummary> settlements, List<UnsettledEntry> unsettledEntries,
                                String nextCursor, boolean hasMore) {
    }

    private final JdbcTemplate jdbc;
    private final KitchenDirectory kitchens;
    private final Clock clock;

    StatementService(JdbcTemplate jdbc, KitchenDirectory kitchens, Clock clock) {
        this.jdbc = jdbc;
        this.kitchens = kitchens;
        this.clock = clock;
    }

    private static final String COMPLETED = """
            SELECT o.id, o.public_number, e.occurred_at AS completed_at, o.total_minor, c.state AS cash_state,
                   c.amount_minor AS cash_minor,
                   (SELECT sum(le.amount_minor) FROM ledger_group g JOIN ledger_entry le ON le.group_id = g.id
                     WHERE g.order_id = o.id AND g.kind = 'COMMISSION' AND le.account = 'COOK_COMMISSION_RECEIVABLE' AND le.direction = 'DEBIT')
                     AS commission_minor
            FROM customer_order o
            JOIN order_event e ON e.order_id = o.id AND e.to_state = 'COMPLETED'
            JOIN cash_obligation c ON c.order_id = o.id
            WHERE o.kitchen_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?""";

    public StatementPage statement(UUID cookId, String fromParam, String toParam, String cursor, int limit) {
        UUID kitchen = kitchens.ownedBy(cookId).map(KitchenDirectory.KitchenStatus::kitchenId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "not a cook"));
        return forKitchen(kitchen, fromParam, toParam, cursor, limit);
    }

    /** API-086: any kitchen, for finance staff. */
    public StatementPage forKitchen(UUID kitchen, String fromParam, String toParam, String cursor, int limit) {
        if (kitchens.find(kitchen).isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "kitchen not found");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        LocalDate today = LocalDate.ofInstant(clock.instant(), CAIRO);
        LocalDate from = date("from", fromParam, today.withDayOfMonth(1));
        LocalDate to = date("to", toParam, today);
        if (to.isBefore(from) || from.plusDays(366).isBefore(to)) {
            throw invalid("to", "RANGE_MAX_366_DAYS");
        }
        Timestamp start = Timestamp.from(from.atStartOfDay(CAIRO).toInstant());
        Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(CAIRO).toInstant());

        List<Map<String, Object>> all = jdbc.queryForList(COMPLETED, kitchen, start, end);
        long ordersTotal = 0;
        long reported = 0;
        long verified = 0;
        long outstanding = 0;
        long commission = 0;
        int pending = 0;
        for (Map<String, Object> r : all) {
            long cash = (Long) r.get("cash_minor");
            ordersTotal += (Long) r.get("total_minor");
            switch ((String) r.get("cash_state")) {
                case "REPORTED" -> reported += cash;
                case "VERIFIED" -> verified += cash;
                case "DUE", "DISPUTED" -> outstanding += cash;
                default -> { }
            }
            Object c = r.get("commission_minor");
            if (c == null) {
                pending++;
            } else {
                commission += ((Number) c).longValue();
            }
        }
        List<UUID> orderIds = all.stream().map(r -> (UUID) r.get("id")).toList();
        UUID[] ids = orderIds.toArray(UUID[]::new);
        long reversed = jdbc.queryForObject("""
                SELECT coalesce(sum(e.amount_minor), 0) FROM ledger_group g JOIN ledger_entry e ON e.group_id = g.id
                WHERE g.kind = 'COMMISSION_REVERSAL' AND e.account = 'COOK_COMMISSION_RECEIVABLE' AND g.order_id = ANY (?)""", Long.class, (Object) ids);
        long settled = jdbc.queryForObject("""
                SELECT coalesce(sum(l.amount_minor), 0) FROM settlement_line l JOIN settlement_batch b ON b.id = l.batch_id
                JOIN ledger_group g ON g.id = l.ledger_group_id WHERE l.active AND b.state = 'RECONCILED' AND g.order_id = ANY (?)""", Long.class, (Object) ids);
        Summary summary = new Summary(all.size(), egp(ordersTotal), egp(reported), egp(verified), egp(outstanding), egp(commission), pending,
                egp(reversed), egp(settled), egp(commission - reversed - settled));
        List<RefundSummary> refunds = jdbc.query("""
                SELECT id, order_id, amount_minor, state, payment_reference, version FROM refund WHERE order_id = ANY (?) ORDER BY created_at, id""",
                (rs, n) -> new RefundSummary(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), egp(rs.getLong(3)), rs.getString(4), rs.getString(5), rs.getLong(6)),
                (Object) ids);
        List<SettlementSummary> settlements = jdbc.query("""
                SELECT id, period_from, period_to, state, total_minor, payment_reference, version FROM settlement_batch
                WHERE kitchen_id = ? AND period_from <= ? AND period_to >= ? ORDER BY created_at, id""",
                (rs, n) -> new SettlementSummary(rs.getObject(1, UUID.class), rs.getObject(2, LocalDate.class), rs.getObject(3, LocalDate.class),
                        rs.getString(4), egp(rs.getLong(5)), rs.getString(6), rs.getLong(7)), kitchen, to, from);
        List<UnsettledEntry> unsettled = jdbc.query("""
                SELECT g.id, g.kind, g.order_id, CASE WHEN g.kind = 'COMMISSION' THEN e.amount_minor ELSE -e.amount_minor END
                FROM ledger_group g JOIN ledger_entry e ON e.group_id = g.id AND e.account = 'COOK_COMMISSION_RECEIVABLE'
                WHERE g.kitchen_id = ? AND g.posted_at >= ? AND g.posted_at < ?
                  AND NOT EXISTS (SELECT 1 FROM settlement_line l WHERE l.ledger_group_id = g.id AND l.active)
                ORDER BY g.posted_at, g.id LIMIT 500""",
                (rs, n) -> new UnsettledEntry(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), egp(rs.getLong(4))),
                kitchen, start, end);

        Instant afterTime = Instant.EPOCH;
        UUID afterId = new UUID(0, 0);
        if (cursor != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 4);
                if (!parts[2].equals(from.toString()) || !parts[3].equals(to.toString())) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "cursor for another period",
                            List.of(new FieldError("cursor", "CURSOR_FILTER_MISMATCH")));
                }
                afterTime = Instant.parse(parts[0]);
                afterId = UUID.fromString(parts[1]);
            } catch (ApiException e) {
                throw e;
            } catch (RuntimeException e) {
                throw invalid("cursor", "INVALID");
            }
        }
        List<Line> rows = jdbc.query(COMPLETED + " AND (e.occurred_at, o.id) > (?, ?) ORDER BY e.occurred_at, o.id LIMIT ?",
                (rs, n) -> {
                    Object c = rs.getObject("commission_minor");
                    return new Line(rs.getObject("id", UUID.class), rs.getString("public_number"), rs.getTimestamp("completed_at").toInstant(),
                            egp(rs.getLong("total_minor")), rs.getString("cash_state"), c == null ? null : egp(((Number) c).longValue()),
                            c == null ? "PENDING_POSTING" : "POSTED");
                }, kitchen, start, end, Timestamp.from(afterTime), afterId, limit + 1);
        boolean more = rows.size() > limit;
        List<Line> page = more ? rows.subList(0, limit) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString((page.getLast().completedAt() + "|" + page.getLast().orderId()
                + "|" + from + "|" + to).getBytes(StandardCharsets.UTF_8)) : null;
        return new StatementPage(kitchen, from, to, "COOK_PAYS_PLATFORM", List.of("COMPLETED_ORDERS", "CASH_POSITION", "COMMISSION", "REFUNDS", "SETTLEMENTS"),
                summary, page, refunds, settlements, unsettled, next, more);
    }

    private static LocalDate date(String field, String value, LocalDate fallback) {
        if (value == null) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(field, "ISO_DATE");
        }
    }

    private static Money egp(long minor) {
        return new Money(minor, "EGP");
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
