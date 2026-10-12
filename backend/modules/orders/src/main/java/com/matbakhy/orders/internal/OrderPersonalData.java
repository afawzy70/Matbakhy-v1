package com.matbakhy.orders.internal;

import com.matbakhy.shared.privacy.PersonalDataEraser;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Account erasure for orders and reviews (MB-039). Erasure waits until the customer has no active order. Terminal
 * orders keep amounts, items, events and stock history; only the contact and address snapshots and the free-text note
 * are replaced (the database trigger allows nothing else). Review text is removed; the rating stays.
 */
@Component
class OrderPersonalData implements PersonalDataEraser {

    private final JdbcTemplate jdbc;

    OrderPersonalData(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String area() {
        return "orders";
    }

    @Override
    public Optional<Blocker> blocker(UUID accountId) {
        Integer active = jdbc.queryForObject("""
                SELECT count(*) FROM customer_order WHERE customer_id = ?
                AND state NOT IN ('COMPLETED', 'REJECTED', 'EXPIRED', 'CANCELLED', 'DELIVERY_FAILED')""", Integer.class, accountId);
        return active != null && active > 0 ? Optional.of(new Blocker("ACTIVE_ORDERS", true)) : Optional.empty();
    }

    @Override
    public Map<String, Integer> erase(UUID accountId) {
        int orders = jdbc.update("""
                UPDATE customer_order SET contact_snapshot = '{"erased": true}'::jsonb,
                       address_snapshot = CASE WHEN address_snapshot IS NULL THEN NULL ELSE '{"erased": true}'::jsonb END,
                       fulfillment_note = NULL, personal_data_erased_at = now()
                WHERE customer_id = ? AND personal_data_erased_at IS NULL""", accountId);
        int reviews = jdbc.update("UPDATE review SET text = NULL WHERE customer_id = ? AND text IS NOT NULL", accountId);
        return Map.of("orderSnapshots", orders, "reviewTexts", reviews);
    }
}
