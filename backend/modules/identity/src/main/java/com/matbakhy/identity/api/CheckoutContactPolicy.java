package com.matbakhy.identity.api;

import java.util.UUID;

/** BR-003: checkout requires a verified email and a verified Egyptian mobile number. Used by quotes/orders (MB-017). */
public interface CheckoutContactPolicy {

    /** Throws {@code ApiException(CONTACT_VERIFICATION_REQUIRED)} unless both contacts are verified. */
    void requireVerifiedContact(UUID accountId);

    record VerifiedContact(String displayName, String phoneE164) {
    }

    /** The verified contact to snapshot on an order; same check as {@link #requireVerifiedContact}. */
    VerifiedContact verifiedContact(UUID accountId);
}
