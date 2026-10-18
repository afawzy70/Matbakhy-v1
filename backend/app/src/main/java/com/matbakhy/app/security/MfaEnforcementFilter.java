package com.matbakhy.app.security;

import com.matbakhy.app.mfa.MfaPolicyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Server-side enforcement of the effective MFA policy (D18). For an active account whose effective policy requires MFA
 * (REQUIRED, or OPTIONAL with the user's own preference on), every protected API call must carry the identity provider's
 * MFA assertion (acr/amr on the token or session); otherwise the call is refused with STEP_UP_REQUIRED and the client
 * performs a Keycloak step-up. The user's preference can only add this requirement, never remove an administrator's.
 * Public reads, the security-settings endpoints (needed to enrol), the profile READ and logout stay reachable; profile writes do not.
 */
public class MfaEnforcementFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();
    /** Reachable without MFA: needed to learn the state, enrol, and sign out. */
    static final List<String> OPEN = List.of("/api/v1/me/security", "/api/v1/me/security/**", "/api/v1/auth/logout");

    private final MfaPolicyService policy;
    private final IdentityProperties properties;

    public MfaEnforcementFilter(MfaPolicyService policy, IdentityProperties properties) {
        this.policy = policy;
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        if (current instanceof AccountAuthentication account && account.getPrincipal().active() && applies(request)) {
            MfaPolicyService.Decision decision = policy.decide(account.getPrincipal().id());
            if (decision.required() && !decision.satisfiedBy(account.identityClaims(), properties.mfaAcrValues())) {
                throw new StepUpRequiredException();
            }
        }
        chain.doFilter(request, response);
    }

    private static boolean applies(HttpServletRequest request) {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/")) {
            return false;
        }
        for (String open : OPEN) {
            if (MATCHER.match(open, path)) {
                return false;
            }
        }
        // Reading one's own profile stays reachable (the client learns who is signed in); changing it needs the assertion.
        if ("/api/v1/me".equals(path) && HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        if (HttpMethod.GET.matches(request.getMethod())) {
            for (String pub : SecurityConfiguration.PUBLIC_GET) {
                if (MATCHER.match(pub, path)) {
                    return false;
                }
            }
        }
        return !(HttpMethod.POST.matches(request.getMethod()) && SecurityConfiguration.ACTIVITY.equals(path));
    }
}
