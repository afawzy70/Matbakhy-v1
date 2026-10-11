package com.matbakhy.cooks.api;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Selling eligibility of kitchens (BR-008, BR-009). A kitchen exists only after its cook application was
 * approved, so an unapproved cook has no kitchen and can neither sell nor accept orders. Meal publication,
 * checkout and order acceptance must ask this directory instead of reading kitchen state themselves.
 */
public interface KitchenDirectory {

    enum OperatingState { ACTIVE, PAUSED, SUSPENDED, CLOSED }

    record KitchenStatus(UUID kitchenId, UUID ownerId, OperatingState state) {

        /** New sales: discovery listing, meal publication, quotes and checkout. Only an active kitchen sells. */
        public boolean canSell() {
            return state == OperatingState.ACTIVE;
        }

        /** Accepting already placed orders. A pause stops new sales only; suspension also stops acceptance. */
        public boolean canAcceptOrders() {
            return state == OperatingState.ACTIVE || state == OperatingState.PAUSED;
        }

        /** Safe fulfillment of orders accepted before a pause or suspension stays available. */
        public boolean canFulfillAcceptedOrders() {
            return state != OperatingState.CLOSED;
        }
    }

    Optional<KitchenStatus> find(UUID kitchenId);

    Optional<KitchenStatus> ownedBy(UUID ownerId);

    /** Private fulfillment contact, only for an order owner after acceptance (never in public DTOs). */
    record FulfillmentContact(String displayName, String contactPhone, String pickupLocation) {
    }

    Optional<FulfillmentContact> fulfillmentContact(UUID kitchenId);

    /** Delivery zone approved for the kitchen and whether the cook currently delivers there. */
    record ZoneSetting(UUID zoneId, String nameAr, String nameEn, boolean deliveryEnabled) {
    }

    /** The owner's own kitchen settings (cook workspace). {@code pausedBy} is COOK or STAFF while PAUSED. */
    record KitchenSettings(UUID kitchenId, String publicSlug, String displayName, String description, String operatingState,
                           String pausedBy, boolean pickupEnabled, List<ZoneSetting> zones, long version) {
    }

    Optional<KitchenSettings> settingsOf(UUID ownerId);
}
