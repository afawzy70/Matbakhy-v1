package com.matbakhy.identity.api;

import java.util.UUID;

/** The local account behind an authenticated request. */
public record CurrentAccount(UUID id, AccountStatus status, String displayName, String locale, boolean emailVerified,
                             boolean phoneVerified, long version) {

    public boolean active() {
        return status == AccountStatus.ACTIVE;
    }
}
