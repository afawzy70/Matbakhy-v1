package com.matbakhy.availability.api;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Read model of slots for discovery and quotes (MB-016/MB-017). Orderability always uses server time. */
public interface SlotDirectory {

    record SlotStatus(UUID slotId, UUID kitchenId, Instant startsAt, Instant endsAt, Instant cutoffAt, String state,
                      int availablePortions, Map<UUID, Integer> mealAvailablePortions) {

        /** OPEN and before cutoff (BR-015); a client clock is never consulted. */
        public boolean orderableAt(Instant now) {
            return "OPEN".equals(state) && now.isBefore(cutoffAt);
        }

        /** Portions of a meal still available: bounded by its own capacity and by the slot aggregate (BR-013). */
        public int availableFor(UUID mealId) {
            return Math.min(availablePortions, mealAvailablePortions.getOrDefault(mealId, 0));
        }
    }

    Optional<SlotStatus> slot(UUID slotId);
}
