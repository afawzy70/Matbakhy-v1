package com.matbakhy.orders.internal;

import com.matbakhy.cooks.api.KitchenOrderImpact;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcKitchenOrderImpact implements KitchenOrderImpact {

    private static final List<String> ACTIVE = List.of("PENDING_ACCEPTANCE", "ACCEPTED", "PREPARING", "READY", "OUT_FOR_DELIVERY");

    private final JdbcTemplate jdbc;

    JdbcKitchenOrderImpact(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Map<String, Integer> activeOrders(UUID kitchenId) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        ACTIVE.forEach(s -> counts.put(s, 0));
        jdbc.query("SELECT state, count(*) FROM customer_order WHERE kitchen_id = ? AND state = ANY (?) GROUP BY state",
                rs -> {
                    counts.put(rs.getString(1), rs.getInt(2));
                }, kitchenId, ACTIVE.toArray(String[]::new));
        return counts;
    }
}
