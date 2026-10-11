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
}
