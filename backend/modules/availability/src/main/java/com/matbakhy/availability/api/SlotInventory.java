package com.matbakhy.availability.api;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Stock reservation inside the caller's transaction (BR-013, BR-025). Locks are taken in a fixed order — the
 * cook slot row, then meal-slot rows by id — so concurrent placements serialize without deadlock; both the meal
 * and the slot aggregate are checked after locking. Throws STOCK_CHANGED or SLOT_CLOSED; nothing is changed then.
 */
public interface SlotInventory {

    record Reservation(UUID mealSlotId, UUID mealId, int quantity) {
    }

    List<Reservation> reserve(UUID slotId, Map<UUID, Integer> quantityByMeal);

    enum Stage { RESERVED, ALLOCATED, CONSUMED }

    record Line(UUID mealSlotId, int quantity) {
    }

    /**
     * Moves portions between counters at meal and slot level with the same lock order: RESERVED to ALLOCATED on
     * acceptance, ALLOCATED to CONSUMED on preparation, or out of {@code from} entirely when {@code to} is null
     * (release). Fails without changes if a counter would go negative. Callers guarantee each move happens once.
     */
    void move(UUID slotId, List<Line> lines, Stage from, Stage to);
}
