package com.matbakhy.app.security;

import com.matbakhy.identity.api.AccessPolicy;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/** Deny-by-default object scope (BR-006) for the authenticated request. */
@Component
class ScopedAccessPolicy implements AccessPolicy {

    private final AuditLog audit;

    ScopedAccessPolicy(AuditLog audit) {
        this.audit = audit;
    }

    @Override
    public void requireOwnerOrPermitted(UUID ownerId, Permission staffPermission, String objectType, String objectId) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof CurrentAccount actor) || !actor.active()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "no active actor");
        }
        if (actor.id().equals(ownerId)) {
            return;
        }
        boolean permitted = staffPermission != null
                && auth.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(staffPermission.authority()));
        if (permitted) {
            audit.record(new Entry(actor.id(), "access." + objectType, staffPermission.code(), objectType, objectId, Outcome.ALLOWED,
                    "staff access to another account's resource", requestId(), Map.of()));
            return;
        }
        throw new ApiException(ErrorCode.NOT_FOUND, objectType + " not visible to caller");
    }

    private static String requestId() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            Object id = attrs.getRequest().getAttribute(com.matbakhy.shared.web.RequestContext.REQUEST_ID_ATTRIBUTE);
            return id == null ? null : id.toString();
        }
        return null;
    }
}
