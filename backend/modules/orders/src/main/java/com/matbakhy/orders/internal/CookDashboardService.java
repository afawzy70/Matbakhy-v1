package com.matbakhy.orders.internal;

import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.cooks.api.KitchenDirectory.KitchenSettings;
import com.matbakhy.orders.internal.OrderService.Money;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * API-045: the cook's own work at a glance, computed by the server: active queue counts, pending orders by
 * acceptance deadline, upcoming slot totals and the kitchen settings. {@code serverTime} lets clients show
 * deadlines without trusting the device clock.
 */
@Service
public class CookDashboardService {

    public record PendingOrder(UUID orderId, String publicNumber, Instant acceptBy, String fulfillmentMode, Money total, long version) {
    }

    public record SlotTotals(UUID slotId, Instant startsAt, Instant endsAt, Instant cutoffAt, String state, int maxPortions, int reserved,
                             int allocated, int consumed) {
    }

    public record CookDashboard(Instant serverTime, KitchenSettings kitchen, Map<String, Integer> activeCounts, List<PendingOrder> pendingAcceptance,
                                List<SlotTotals> upcomingSlots) {
    }

    private final JdbcTemplate jdbc;
    private final KitchenDirectory kitchens;
    private final Clock clock;

    CookDashboardService(JdbcTemplate jdbc, KitchenDirectory kitchens, Clock clock) {
        this.jdbc = jdbc;
        this.kitchens = kitchens;
        this.clock = clock;
    }

    public CookDashboard dashboard(UUID cookId) {
        KitchenSettings kitchen = kitchens.settingsOf(cookId).orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "not a cook"));
        Instant now = clock.instant();
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String state : List.of("PENDING_ACCEPTANCE", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY")) {
            counts.put(state, 0);
        }
        jdbc.query("SELECT state, count(*) FROM customer_order WHERE kitchen_id = ? AND state IN "
                + "('PENDING_ACCEPTANCE', 'ACCEPTED', 'PREPARING', 'READY', 'OUT_FOR_DELIVERY') GROUP BY state",
                rs -> {
                    counts.put(rs.getString(1), rs.getInt(2));
                }, kitchen.kitchenId());
        List<PendingOrder> pending = jdbc.query("""
                SELECT id, public_number, accept_by, fulfillment_mode, total_minor, version FROM customer_order
                WHERE kitchen_id = ? AND state = 'PENDING_ACCEPTANCE' ORDER BY accept_by, id LIMIT 20""",
                (rs, n) -> new PendingOrder(rs.getObject(1, UUID.class), rs.getString(2), rs.getTimestamp(3).toInstant(), rs.getString(4),
                        new Money(rs.getLong(5), "EGP"), rs.getLong(6)), kitchen.kitchenId());
        List<SlotTotals> slots = jdbc.query("""
                SELECT id, starts_at, ends_at, cutoff_at, state, max_portions, reserved, allocated, consumed FROM cook_slot
                WHERE kitchen_id = ? AND ends_at > ? AND state <> 'CANCELLED' ORDER BY starts_at, id LIMIT 10""",
                (rs, n) -> new SlotTotals(rs.getObject(1, UUID.class), rs.getTimestamp(2).toInstant(), rs.getTimestamp(3).toInstant(),
                        rs.getTimestamp(4).toInstant(), rs.getString(5), rs.getInt(6), rs.getInt(7), rs.getInt(8), rs.getInt(9)),
                kitchen.kitchenId(), Timestamp.from(now));
        return new CookDashboard(now, kitchen, counts, pending, slots);
    }
}
