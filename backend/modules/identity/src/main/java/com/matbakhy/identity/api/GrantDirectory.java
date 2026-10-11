package com.matbakhy.identity.api;

import java.util.Set;
import java.util.UUID;

/** Current, unexpired, unrevoked staff permissions. Read on every request so revocation takes effect at once. */
public interface GrantDirectory {

    Set<Permission> activePermissions(UUID accountId);

    /** Grants STAFF_GRANT and AUDIT_READ to the configured bootstrap identity only while nobody holds STAFF_GRANT. */
    void bootstrapAdministratorIfNone(UUID accountId);
}
