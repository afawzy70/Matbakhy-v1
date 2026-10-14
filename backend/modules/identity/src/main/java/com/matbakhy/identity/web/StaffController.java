package com.matbakhy.identity.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.internal.GrantService;
import com.matbakhy.identity.internal.GrantService.Grant;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-091..093. Changing grants needs staff.grant plus recent MFA (step-up). */
@RestController
class StaffController {

    private final GrantService grants;

    StaffController(GrantService grants) {
        this.grants = grants;
    }

    record GrantRequest(@NotBlank @Size(max = 64) String permission, Instant expiresAt, @NotBlank @Size(min = 3, max = 500) String reason) {
    }

    record RevocationRequest(@NotNull UUID grantId, @NotBlank @Size(min = 3, max = 500) String reason) {
    }

    record StaffMember(UUID userId, String displayName, String status, List<Map<String, Object>> grants) {
    }

    record StaffPage(List<StaffMember> items, String nextCursor, boolean hasMore) {
    }

    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(com.matbakhy.shared.web.RequestContext.REQUEST_ID_ATTRIBUTE);
        return id == null ? null : id.toString();
    }

    @GetMapping("/api/v1/ops/staff")
    @PreAuthorize("hasAuthority('PERM_staff.grant')")
    StaffPage staff() {
        Map<UUID, StaffMember> members = new LinkedHashMap<>();
        for (Map<String, Object> row : grants.staff()) {
            UUID user = (UUID) row.get("user_id");
            members.computeIfAbsent(user, u -> new StaffMember(u, (String) row.get("display_name"), (String) row.get("status"), new ArrayList<>()))
                    .grants().add(Map.of("grantId", row.get("grant_id"), "permission", row.get("permission")));
        }
        return new StaffPage(List.copyOf(members.values()), null, false);
    }

    @PostMapping("/api/v1/ops/staff/{userId}/grants")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_staff.grant') and @staffAssurance.recentMfa(authentication)")
    Grant grant(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID userId, @Valid @RequestBody GrantRequest body,
                HttpServletRequest request) {
        return grants.grant(actor.id(), userId, body.permission(), body.expiresAt(), body.reason(), requestId(request));
    }

    @PostMapping("/api/v1/ops/staff/{userId}/grant-revocations")
    @PreAuthorize("hasAuthority('PERM_staff.grant') and @staffAssurance.recentMfa(authentication)")
    Grant revoke(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID userId, @Valid @RequestBody RevocationRequest body,
                 HttpServletRequest request) {
        return grants.revoke(actor.id(), userId, body.grantId(), body.reason(), requestId(request));
    }
}
