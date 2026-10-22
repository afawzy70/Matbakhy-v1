package com.matbakhy.app.mfa;

import com.matbakhy.app.mfa.MfaPolicyService.Category;
import com.matbakhy.app.mfa.MfaPolicyService.Mode;
import com.matbakhy.app.mfa.MfaPolicyService.Policy;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrator controls for MFA (D18): the policy per account category and the audited recovery reset. Both need their own
 * permission and a recent second factor. The reset removes only OTP credentials at Keycloak and ends the user's sessions;
 * no operation here can read an OTP secret.
 */
@RestController
class MfaAdminController {

    private final MfaPolicyService policy;
    private final IdentityProviderAdmin keycloak;
    private final ApplicationSessions sessions;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;

    MfaAdminController(MfaPolicyService policy, IdentityProviderAdmin keycloak, ApplicationSessions sessions, AuditLog audit, JdbcTemplate jdbc) {
        this.policy = policy;
        this.keycloak = keycloak;
        this.sessions = sessions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    record PolicyList(List<Policy> items) {
    }

    record PolicyBody(@NotNull Mode mode, @NotNull Long expectedVersion, @NotBlank @Size(min = 3, max = 500) String reason) {
    }

    record ResetBody(@NotBlank @Size(min = 10, max = 500) String reason) {
    }

    record ResetResult(UUID accountId, int removedFactors, boolean sessionsEnded) {
    }

    @GetMapping("/api/v1/ops/security/mfa-policy")
    @PreAuthorize("hasAuthority('PERM_security.mfa_policy')")
    PolicyList policies() {
        return new PolicyList(policy.policies());
    }

    @PutMapping("/api/v1/ops/security/mfa-policy/{category}")
    @PreAuthorize("hasAuthority('PERM_security.mfa_policy') and @staffAssurance.recentMfa(authentication)")
    Policy update(@AuthenticationPrincipal CurrentAccount actor, @PathVariable Category category, @Valid @RequestBody PolicyBody body,
                  HttpServletRequest request) {
        return policy.update(actor.id(), category, body.mode(), body.expectedVersion(), body.reason(), MfaController.requestId(request));
    }

    @PostMapping("/api/v1/ops/accounts/{accountId}/mfa-reset")
    @PreAuthorize("hasAuthority('PERM_security.mfa_reset') and @staffAssurance.recentMfa(authentication)")
    ResetResult reset(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID accountId, @Valid @RequestBody ResetBody body,
                      HttpServletRequest request) {
        String requestId = MfaController.requestId(request);
        if (actor.id().equals(accountId)) {
            audit.record(new Entry(actor.id(), "mfa.reset", "security.mfa_reset", "account", accountId.toString(), Outcome.DENIED,
                    "self reset refused", requestId, Map.of()));
            throw new ApiException(ErrorCode.FORBIDDEN, "an administrator cannot reset their own MFA");
        }
        List<String> subjects = jdbc.queryForList("SELECT subject FROM app_user WHERE id = ?", String.class, accountId);
        if (subjects.isEmpty()) {
            throw new ApiException(ErrorCode.NOT_FOUND, "account not found");
        }
        if (!keycloak.configured()) {
            throw new ApiException(ErrorCode.DEPENDENCY_UNAVAILABLE, "identity provider admin access not configured",
                    List.of(new FieldError("mfa", "ADMIN_ACCESS_NOT_CONFIGURED")));
        }
        String subject = subjects.getFirst();
        int removed = 0;
        // Durable evidence of the attempt exists before anything destructive is done.
        audit.record(new Entry(actor.id(), "mfa.reset.attempt", "security.mfa_reset", "account", accountId.toString(), Outcome.ALLOWED,
                body.reason().strip(), requestId, Map.of()));
        try {
            // First: every earlier MFA assertion of the account stops counting, whatever else fails below.
            policy.recordReset(accountId, actor.id());
            // The application's own sessions end before any provider call, so a provider outage cannot leave them usable.
            sessions.endAllFor(subject);
            for (String credentialId : keycloak.otpCredentialIds(subject)) {
                keycloak.deleteCredential(subject, credentialId);
                removed++;
            }
            keycloak.logout(subject);
        } catch (RuntimeException failure) {
            // A reset that stops half way is still an administrative act on the account: it is audited with what was done.
            Map<String, Object> partial = new LinkedHashMap<>();
            partial.put("removedFactors", removed);
            partial.put("completed", false);
            audit.record(new Entry(actor.id(), "mfa.reset", "security.mfa_reset", "account", accountId.toString(), Outcome.DENIED,
                    "reset incomplete: " + failure.getClass().getSimpleName() + "; " + body.reason().strip(), requestId, partial));
            throw failure;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("removedFactors", removed);
        details.put("sessionsEnded", true);
        audit.record(new Entry(actor.id(), "mfa.reset", "security.mfa_reset", "account", accountId.toString(), Outcome.ALLOWED,
                body.reason().strip(), requestId, details));
        return new ResetResult(accountId, removed, true);
    }
}
