package com.matbakhy.orders.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Revalidation of a customer's quote at order placement (BR-020, MB-018). Anything that changed since quoting
 * (expiry, policy, meal revision or availability, kitchen, slot, address or fee, contact verification) is a 409 with
 * a recoverable reason in {@code fieldErrors}; the customer must request a fresh quote and confirm again.
 */
public interface QuoteDirectory {

    record QuotedItem(UUID mealId, int mealRevision, int quantity, long unitPriceMinor, long lineTotalMinor) {
    }

    record ValidQuote(UUID quoteId, UUID customerId, UUID kitchenId, UUID slotId, String fulfillmentMode, UUID addressId,
                      Long addressVersion, UUID zoneId, int policyVersion, long subtotalMinor, long deliveryFeeMinor, long taxMinor,
                      long totalMinor, Instant acceptByPreview, Instant expiresAt, List<QuotedItem> items) {
    }

    ValidQuote requireValid(UUID customerId, UUID quoteId);
}
