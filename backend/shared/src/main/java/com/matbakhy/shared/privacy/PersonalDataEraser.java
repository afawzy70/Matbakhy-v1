package com.matbakhy.shared.privacy;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * One module's part of account erasure (MB-039, TC-055). The identity module calls every implementation inside one
 * transaction once a deletion request is due; each module erases only its own tables, so no module writes another's.
 * Financial records and order amounts are kept; personal data is removed or replaced by an erasure marker.
 */
public interface PersonalDataEraser {

    /** Short stable name used in the audit record. */
    String area();

    /**
     * Why the account cannot be erased yet, if anything. {@code retryLater} reasons (e.g. active orders) are tried again
     * on a later run; others put the request on hold for staff review.
     */
    default Optional<Blocker> blocker(UUID accountId) {
        return Optional.empty();
    }

    /** Erases this module's personal data for the account and returns counts by kind for the audit record. */
    Map<String, Integer> erase(UUID accountId);

    record Blocker(String reason, boolean retryLater) {
    }
}
