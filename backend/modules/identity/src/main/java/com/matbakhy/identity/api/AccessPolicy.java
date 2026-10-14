package com.matbakhy.identity.api;

import java.util.UUID;

/**
 * Object-level authorization (BR-006). The owner may access; staff may access only with the named
 * permission (and that access is audited); everyone else gets 404 so existence is not revealed.
 */
public interface AccessPolicy {

    void requireOwnerOrPermitted(UUID ownerId, Permission staffPermission, String objectType, String objectId);
}
