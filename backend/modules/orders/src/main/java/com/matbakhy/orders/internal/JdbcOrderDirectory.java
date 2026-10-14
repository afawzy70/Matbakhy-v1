package com.matbakhy.orders.internal;

import com.matbakhy.orders.api.OrderDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcOrderDirectory implements OrderDirectory {

    private final JdbcTemplate jdbc;

    JdbcOrderDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Parties> parties(UUID orderId) {
        return jdbc.query("SELECT id, public_number, customer_id, kitchen_id FROM customer_order WHERE id = ?",
                (rs, n) -> new Parties(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class)),
                orderId).stream().findFirst();
    }

    @Override
    public Optional<Status> status(UUID orderId) {
        return jdbc.query("SELECT id, public_number, customer_id, kitchen_id, state, version FROM customer_order WHERE id = ?",
                (rs, n) -> new Status(rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3, UUID.class), rs.getObject(4, UUID.class),
                        rs.getString(5), rs.getLong(6)), orderId).stream().findFirst();
    }
}
