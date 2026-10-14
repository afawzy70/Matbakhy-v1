package com.matbakhy.finance.internal;

import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.finance.internal.CashService.Money;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * API-111 (MB-054, owner decision D17): the free cook report for the cook's own kitchen and a Cairo date window.
 * Activity comes from the deduplicated organic activity table; orders, capacity and money come only from the server's
 * own records. Gross sales, paid refunds and the known net are kept apart, and nothing here is or claims profit: the
 * platform does not know the kitchen's costs. There is no ranking, campaign or billing input or output.
 */
@Service
public class InsightsService {

    static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    static final List<String> DEFINITIONS = List.of("ACTIVITY_DISTINCT_VISITORS_PER_DAY", "ORDERS_BY_EVENT_TIME_CAIRO",
            "GROSS_IS_COMPLETED_ORDER_TOTALS", "REFUNDS_PAID_OR_VERIFIED", "NET_IS_GROSS_MINUS_REFUNDS", "NET_IS_NOT_PROFIT",
            "REPEAT_IS_TWO_OR_MORE_COMPLETED_IN_WINDOW", "CAPACITY_BY_SLOT_START");

    public record Activity(int mealImpressions, int mealViews, int kitchenViews, int addToCart) {
    }

    public record Orders(int quotes, int placed, int completed, int cancelled, int rejected, int expired, int deliveryFailed,
                         int repeatCustomers) {
    }

    public record Capacity(int portionsOffered, int portionsTaken) {
    }

    public record MoneySummary(Money grossSales, Money refunds, Money netKnown) {
    }

    public record CookInsights(UUID kitchenId, LocalDate from, LocalDate to, String timezone, Activity activity, Orders orders,
                               Capacity capacity, MoneySummary money, List<String> definitions) {
    }

    private final JdbcTemplate jdbc;
    private final KitchenDirectory kitchens;
    private final Clock clock;

    InsightsService(JdbcTemplate jdbc, KitchenDirectory kitchens, Clock clock) {
        this.jdbc = jdbc;
        this.kitchens = kitchens;
        this.clock = clock;
    }

    public CookInsights forCook(UUID cookId, String fromParam, String toParam) {
        UUID kitchen = kitchens.ownedBy(cookId).map(KitchenDirectory.KitchenStatus::kitchenId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "not a cook"));
        LocalDate today = LocalDate.ofInstant(clock.instant(), CAIRO);
        LocalDate from = date("from", fromParam, today.minusDays(29));
        LocalDate to = date("to", toParam, today);
        if (to.isBefore(from) || from.plusDays(366).isBefore(to)) {
            throw invalid("to", "RANGE_MAX_366_DAYS");
        }
        Timestamp start = Timestamp.from(from.atStartOfDay(CAIRO).toInstant());
        Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(CAIRO).toInstant());

        Map<String, Integer> activity = new HashMap<>();
        jdbc.query("SELECT kind, count(*) FROM activity_daily WHERE kitchen_id = ? AND day BETWEEN ? AND ? GROUP BY kind",
                rs -> { activity.put(rs.getString(1), rs.getInt(2)); }, kitchen, Date.valueOf(from), Date.valueOf(to));

        Map<String, Integer> ended = new HashMap<>();
        jdbc.query("""
                SELECT e.to_state, count(DISTINCT e.order_id) FROM order_event e JOIN customer_order o ON o.id = e.order_id
                WHERE o.kitchen_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?
                  AND e.to_state IN ('COMPLETED', 'CANCELLED', 'REJECTED', 'EXPIRED', 'DELIVERY_FAILED')
                GROUP BY e.to_state""", rs -> { ended.put(rs.getString(1), rs.getInt(2)); }, kitchen, start, end);
        int quotes = count("SELECT count(*) FROM quote WHERE kitchen_id = ? AND created_at >= ? AND created_at < ?", kitchen, start, end);
        int placed = count("SELECT count(*) FROM customer_order WHERE kitchen_id = ? AND created_at >= ? AND created_at < ?", kitchen, start, end);
        int repeat = count("""
                SELECT count(*) FROM (
                  SELECT o.customer_id FROM customer_order o JOIN order_event e ON e.order_id = o.id AND e.to_state = 'COMPLETED'
                  WHERE o.kitchen_id = ? AND e.occurred_at >= ? AND e.occurred_at < ?
                  GROUP BY o.customer_id HAVING count(DISTINCT o.id) >= 2) r""", kitchen, start, end);

        Map<String, Object> slots = jdbc.queryForMap("""
                SELECT coalesce(sum(max_portions), 0) AS offered, coalesce(sum(reserved + allocated + consumed), 0) AS taken
                FROM cook_slot WHERE kitchen_id = ? AND starts_at >= ? AND starts_at < ? AND state NOT IN ('DRAFT', 'CANCELLED')""",
                kitchen, start, end);

        long gross = jdbc.queryForObject("""
                SELECT coalesce(sum(o.total_minor), 0) FROM customer_order o
                WHERE o.kitchen_id = ? AND EXISTS (SELECT 1 FROM order_event e WHERE e.order_id = o.id AND e.to_state = 'COMPLETED'
                                                     AND e.occurred_at >= ? AND e.occurred_at < ?)""", Long.class, kitchen, start, end);
        long refunds = jdbc.queryForObject("""
                SELECT coalesce(sum(amount_minor), 0) FROM refund
                WHERE kitchen_id = ? AND state IN ('PAID', 'VERIFIED') AND created_at >= ? AND created_at < ?""", Long.class, kitchen, start, end);

        return new CookInsights(kitchen, from, to, CAIRO.getId(),
                new Activity(activity.getOrDefault("MEAL_IMPRESSION", 0), activity.getOrDefault("MEAL_VIEW", 0),
                        activity.getOrDefault("KITCHEN_VIEW", 0), activity.getOrDefault("ADD_TO_CART", 0)),
                new Orders(quotes, placed, ended.getOrDefault("COMPLETED", 0), ended.getOrDefault("CANCELLED", 0),
                        ended.getOrDefault("REJECTED", 0), ended.getOrDefault("EXPIRED", 0), ended.getOrDefault("DELIVERY_FAILED", 0), repeat),
                new Capacity(((Number) slots.get("offered")).intValue(), ((Number) slots.get("taken")).intValue()),
                new MoneySummary(egp(gross), egp(refunds), egp(gross - refunds)), DEFINITIONS);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private static Money egp(long minor) {
        return new Money(minor, "EGP");
    }

    private static LocalDate date(String field, String value, LocalDate fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            throw invalid(field, "ISO_DATE");
        }
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
