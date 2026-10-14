package com.matbakhy.orders.api;

import java.util.Optional;
import java.util.UUID;

/** Who an order concerns and its current state, for notifications and support. No contact details or amounts. */
public interface OrderDirectory {

    record Parties(UUID orderId, String publicNumber, UUID customerId, UUID kitchenId) {
    }

    record Status(UUID orderId, String publicNumber, UUID customerId, UUID kitchenId, String state, long version) {
    }

    Optional<Parties> parties(UUID orderId);

    Optional<Status> status(UUID orderId);
}
