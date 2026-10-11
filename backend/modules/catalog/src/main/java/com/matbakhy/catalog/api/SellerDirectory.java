package com.matbakhy.catalog.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Implemented by the module that owns kitchens (cooks), so the catalog can apply selling eligibility (BR-008,
 * BR-009, BR-011) without a module cycle. Only approved kitchens exist; {@code canSell} is false unless ACTIVE.
 */
public interface SellerDirectory {

    record Seller(UUID kitchenId, UUID ownerId, String publicSlug, String displayName, boolean canSell, boolean pickupEnabled,
                  java.util.Set<UUID> deliveryZoneIds) {
    }

    Optional<Seller> byOwner(UUID ownerId);

    Optional<Seller> byKitchen(UUID kitchenId);
}
