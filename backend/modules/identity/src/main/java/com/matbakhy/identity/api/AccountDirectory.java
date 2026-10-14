package com.matbakhy.identity.api;

import java.util.Optional;
import java.util.UUID;

/** Maps provider identities to local accounts. Grants and roles are never derived from token claims. */
public interface AccountDirectory {

    /** Returns the account for this provider identity, creating it (ACTIVE) on first sight. Idempotent and race-safe. */
    CurrentAccount resolve(ExternalIdentity identity);

    Optional<CurrentAccount> find(UUID accountId);
}
