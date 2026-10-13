package com.matbakhy.catalog.api;

import java.util.Collection;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Current sellable meal terms and delivery fees for quotes (BR-019, BR-021). Prices are integer piastres. */
public interface MealCatalog {

    record PricedMeal(UUID mealId, UUID kitchenId, boolean published, int revision, long priceMinor, String titleAr, String titleEn) {
    }

    Optional<PricedMeal> current(UUID mealId);

    /** Immutable content snapshot (JSON) of a meal revision, copied into order items (BR-012). */
    Optional<String> revisionSnapshot(UUID mealId, int revision);

    /**
     * Owner decision D13: share-locks the rows that price a quote (the meals and, for delivery, the kitchen's zone row and
     * the zone) in the caller's transaction, in a stable order. A concurrent kitchen price edit or fee change (which locks
     * these rows for update) then commits strictly before or after the placement that revalidates against them.
     */
    void lockPricing(Collection<UUID> mealIds, UUID kitchenId, UUID zoneIdOrNull);

    /** Delivery fee of a kitchen to an active zone it delivers to (zone fee or kitchen override); empty if not served. */
    OptionalLong deliveryFee(UUID kitchenId, UUID zoneId);
}
