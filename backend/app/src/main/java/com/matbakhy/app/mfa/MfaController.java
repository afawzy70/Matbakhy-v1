package com.matbakhy.app.mfa;

import com.matbakhy.app.mfa.MfaPolicyService.Category;
import com.matbakhy.app.mfa.MfaPolicyService.Decision;
import com.matbakhy.app.mfa.MfaPolicyService.Mode;
import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.IdentityProperties;
import com.matbakhy.app.security.StaffAssurance;
import com.matbakhy.app.security.StepUpRequiredException;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * Account "Security" settings (D18). Enrolment and removal of the authenticator happen in Keycloak (its account console and
 * authentication flow); this API reports the state, records the user's own preference and refuses anything the
 * administrator's policy forbids.
 */
@RestController
class MfaController {

    private static final Logger log = LoggerFactory.getLogger(MfaController.class);

    private final MfaPolicyService policy;
    private final StaffAssurance assurance;
    private final IdentityProperties identity;
    private final MfaProperties props;
    private final IdentityProviderAdmin admin;
    private final ApplicationSessions sessions;
    private final AuditLog audit;
    private final JdbcTemplate jdbc;

    MfaController(MfaPolicyService policy, StaffAssurance assurance, IdentityProperties identity, MfaProperties props,
                  IdentityProviderAdmin admin, ApplicationSessions sessions, AuditLog audit, JdbcTemplate jdbc) {
        this.policy = policy;
        this.assurance = assurance;
        this.identity = identity;
        this.props = props;
        this.admin = admin;
        this.sessions = sessions;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    record Security(List<Category> categories, Map<Category, Mode> policy, Mode effectiveMode, boolean enabled, boolean required,
                    boolean available, boolean verifiedInSession, String stepUpPath, String accountManagementUrl,
                    boolean canManagePolicy, boolean canResetMfa) {
    }

    record PreferenceBody(@NotNull Boolean enabled) {
    }

    private Security view(Decision d, boolean verified, Authentication authentication) {
        String console = props.accountConsoleUrl() != null && !props.accountConsoleUrl().isBlank()
                ? props.accountConsoleUrl() : identity.issuer() + "/account/#/security/signingin";
        return new Security(List.copyOf(d.categories()), d.modes(), d.effectiveMode(), d.preference(), d.required(), d.available(), verified,
                "/oauth2/authorization/keycloak?stepUp=true", console, has(authentication, "PERM_security.mfa_policy"),
                has(authentication, "PERM_security.mfa_reset"));
    }

    private static boolean has(Authentication authentication, String authority) {
        return authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(authority));
    }

    private boolean verified(AccountAuthentication a, Decision d) {
        return d.satisfiedBy(a.identityClaims(), identity.mfaAcrValues());
    }

    @GetMapping("/api/v1/me/security")
    Security security(@AuthenticationPrincipal CurrentAccount actor, Authentication authentication) {
        Decision d = policy.decide(actor.id());
        return view(d, authentication instanceof AccountAuthentication a && verified(a, d), authentication);
    }

    @PutMapping("/api/v1/me/security/mfa")
    @Transactional
    Security preference(@AuthenticationPrincipal CurrentAccount actor, Authentication authentication, @Valid @RequestBody PreferenceBody body,
                        HttpServletRequest request) {
        AccountAuthentication account = (AccountAuthentication) authentication;
        Decision before = policy.decide(actor.id());
        boolean enable = body.enabled();
        if (enable) {
            if (!before.available()) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "MFA is disabled for this account category",
                        List.of(new FieldError("enabled", "MFA_UNAVAILABLE")));
            }
            // Enabling is only accepted on a session that has just proved a second factor, so nobody locks themselves out.
            if (!verified(account, before)) {
                throw new StepUpRequiredException();
            }
        } else if (before.preference() || before.required()) {
            if (before.effectiveMode() == Mode.REQUIRED) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "MFA is required by policy and cannot be turned off",
                        List.of(new FieldError("enabled", "MFA_REQUIRED_BY_POLICY")));
            }
            // A recent second factor before weakening the account (throws step-up otherwise), and not one older than a reset.
            if (!before.satisfiedBy(account.identityClaims(), identity.mfaAcrValues())) {
                throw new StepUpRequiredException();
            }
            assurance.recentMfa(authentication);
        }
        if (before.preference() != enable) {
            policy.setPreference(actor.id(), enable);
            audit.record(new Entry(actor.id(), enable ? "mfa.preference.enable" : "mfa.preference.disable", null, "account", actor.id().toString(),
                    Outcome.ALLOWED, null, requestId(request), new LinkedHashMap<>(Map.of("effectiveMode", before.effectiveMode().name()))));
            if (!enable) {
                endSessions(actor.id());
            }
        }
        Decision after = policy.decide(actor.id());
        return view(after, verified(account, after), authentication);
    }

    /** Turning MFA off weakens the account: end its sessions at the provider so the next sign-in starts clean. */
    private void endSessions(UUID accountId) {
        String subject = subjectOf(accountId);
        sessions.endAllFor(subject); // the application's own web sessions always end
        if (!admin.configured()) {
            log.info("MFA turned off; provider admin access not configured, provider sessions were not ended");
            return;
        }
        try {
            admin.logout(subject);
        } catch (RuntimeException e) {
            log.warn("MFA turned off; ending provider sessions failed ({})", e.getClass().getSimpleName());
        }
    }

    String subjectOf(UUID accountId) {
        return jdbc.queryForObject("SELECT subject FROM app_user WHERE id = ?", String.class, accountId);
    }

    static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(com.matbakhy.shared.web.RequestContext.REQUEST_ID_ATTRIBUTE);
        return id == null ? null : id.toString();
    }
}
