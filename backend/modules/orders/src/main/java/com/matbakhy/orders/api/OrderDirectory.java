package com.matbakhy.orders.api;

import java.util.Optional;
import java.util.UUID;

/** Who an order concerns, for notifications. No contact details or amounts. */
public interface OrderDirectory {

    record Parties(UUID orderId, String publicNumber, UUID customerId, UUID kitchenId) {
    }

    Optional<Parties> parties(UUID orderId);
}
