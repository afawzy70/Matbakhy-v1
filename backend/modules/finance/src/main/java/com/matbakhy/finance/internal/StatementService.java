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
 * commission receivable. Cash the cook collected is the cook's money; only the commission is owed to the platform.
 * Refunds, reversals and remittances are not part of it until finance operations exist (MB-025); {@code includes}
 * says what the statement covers so clients never imply those are zero.
 */
@Service
public class StatementService {

    static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    public record Line(UUID orderId, String publicNumber, Instant completedAt, Money orderTotal, String cashState, Money commission,
                       String commissionState) {
    }

    public record Summary(int completedOrders, Money ordersTotal, Money cashReported, Money cashVerified, Money cashOutstanding,
                          Money commissionPosted, int commissionPending) {
    }

    public record StatementPage(LocalDate from, LocalDate to, List<String> includes, Summary summary, List<Line> items, String nextCursor,
                                boolean hasMore) {
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
        Summary summary = new Summary(all.size(), egp(ordersTotal), egp(reported), egp(verified), egp(outstanding), egp(commission), pending);

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
        return new StatementPage(from, to, List.of("COMPLETED_ORDERS", "CASH_POSITION", "COMMISSION"), summary, page, next, more);
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
