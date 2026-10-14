package com.matbakhy.identity.api;

import java.util.Optional;
import java.util.UUID;

/** Owner-scoped address facts for quotes and orders (BR-005, BR-021). Another account's address is empty. */
public interface AddressDirectory {

    record OwnedAddress(UUID id, UUID zoneId, long version, String recipientName, String phoneE164, String governorate, String city,
                        String street, String building, String floor, String unit, String landmark) {
    }

    Optional<OwnedAddress> owned(UUID accountId, UUID addressId);
}
