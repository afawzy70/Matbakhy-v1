package com.matbakhy.catalog.api;

import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/** Current sellable meal terms and delivery fees for quotes (BR-019, BR-021). Prices are integer piastres. */
public interface MealCatalog {

    record PricedMeal(UUID mealId, UUID kitchenId, boolean published, int revision, long priceMinor, String titleAr, String titleEn) {
    }

    Optional<PricedMeal> current(UUID mealId);

    /** Delivery fee of a kitchen to an active zone it delivers to (zone fee or kitchen override); empty if not served. */
    OptionalLong deliveryFee(UUID kitchenId, UUID zoneId);
}
