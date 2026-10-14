package com.matbakhy.availability.api;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Implemented by the catalog (which knows kitchens through the cooks module and owns meals), so availability
 * stays free of upstream module dependencies and discovery can build on availability without a cycle.
 */
public interface SlotParticipants {

    record Kitchen(UUID kitchenId, boolean canSell, boolean pickupEnabled, Set<UUID> deliveryZoneIds) {
    }

    record Meal(UUID mealId, UUID kitchenId, boolean archived) {
    }

    Optional<Kitchen> kitchenOwnedBy(UUID userId);

    Optional<Kitchen> kitchen(UUID kitchenId);

    Optional<Meal> meal(UUID mealId);
}
