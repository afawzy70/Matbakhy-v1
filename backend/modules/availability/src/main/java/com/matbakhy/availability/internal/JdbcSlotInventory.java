package com.matbakhy.availability.internal;

import com.matbakhy.availability.api.SlotInventory;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
class JdbcSlotInventory implements SlotInventory {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    JdbcSlotInventory(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private record MealSlotRow(UUID id, UUID mealId, int remaining) {
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Reservation> reserve(UUID slotId, Map<UUID, Integer> quantityByMeal) {
        if (quantityByMeal.isEmpty() || quantityByMeal.values().stream().anyMatch(q -> q == null || q < 1)) {
            throw new IllegalArgumentException("reservation needs positive quantities");
        }
        // 1. Slot aggregate row first.
        Integer slotRemaining = jdbc.query("""
                SELECT max_portions - reserved - allocated - consumed FROM cook_slot
                WHERE id = ? AND state = 'OPEN' AND cutoff_at > ? FOR UPDATE""",
                rs -> rs.next() ? rs.getInt(1) : null, slotId, Timestamp.from(clock.instant()));
        if (slotRemaining == null) {
            throw new ApiException(ErrorCode.SLOT_CLOSED, "slot not orderable", List.of(new FieldError("slotId", "SLOT_CLOSED")));
        }
        // 2. Meal rows in id order.
        List<MealSlotRow> rows = jdbc.query("""
                SELECT id, meal_id, capacity - reserved - allocated - consumed FROM meal_slot
                WHERE cook_slot_id = ? AND meal_id = ANY (?) ORDER BY id FOR UPDATE""",
                (rs, n) -> new MealSlotRow(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getInt(3)),
                slotId, quantityByMeal.keySet().toArray(UUID[]::new));
        if (rows.size() != quantityByMeal.size()) {
            throw stock();
        }
        int total = 0;
        for (MealSlotRow row : rows) {
            int wanted = quantityByMeal.get(row.mealId());
            if (wanted > row.remaining()) {
                throw stock();
            }
            total += wanted;
        }
        if (total > slotRemaining) {
            throw stock();
        }
        List<Reservation> out = new ArrayList<>();
        for (MealSlotRow row : rows) {
            int wanted = quantityByMeal.get(row.mealId());
            jdbc.update("UPDATE meal_slot SET reserved = reserved + ?, version = version + 1 WHERE id = ?", wanted, row.id());
            out.add(new Reservation(row.id(), row.mealId(), wanted));
        }
        jdbc.update("UPDATE cook_slot SET reserved = reserved + ?, version = version + 1, updated_at = ? WHERE id = ?", total,
                Timestamp.from(clock.instant()), slotId);
        return out;
    }

    private static ApiException stock() {
        return new ApiException(ErrorCode.STOCK_CHANGED, "not enough portions", List.of(new FieldError("items", "INSUFFICIENT_PORTIONS")));
    }
}
