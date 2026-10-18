package com.matbakhy.app.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

/**
 * Staff MFA / step-up check (D08, BR-007). Passes only when the provider asserted an MFA-level ACR
 * (or an MFA method in amr) and authentication happened within the step-up window. A frontend flag or
 * a client-supplied header can never satisfy it.
 */
@Component("staffAssurance")
public class StaffAssurance {

    private final IdentityProperties properties;
    private final Clock clock;

    StaffAssurance(IdentityProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    /** True when the provider asserted an MFA-level ACR or an MFA method in amr (no recency check). */
    static boolean assertsMfa(java.util.Map<String, Object> claims, java.util.List<String> mfaAcrValues) {
        boolean mfa = claims.get("acr") != null && mfaAcrValues.contains(claims.get("acr").toString());
        if (!mfa && claims.get("amr") instanceof Collection<?> methods) {
            mfa = methods.stream().map(Object::toString).anyMatch(m -> m.equals("mfa") || m.equals("otp") || m.equals("hwk"));
        }
        return mfa;
    }

    public boolean recentMfa(Authentication authentication) {
        if (!(authentication instanceof AccountAuthentication account) || !account.getPrincipal().active()) {
            return false;
        }
        var claims = account.identityClaims();
        boolean mfa = assertsMfa(claims, properties.mfaAcrValues());
        Instant authTime = toInstant(claims.get("auth_time"));
        boolean recent = authTime != null && !authTime.isBefore(clock.instant().minus(properties.stepUpMaxAge()));
        if (!(mfa && recent)) {
            throw new StepUpRequiredException();
        }
        return true;
    }

    private static Instant toInstant(Object value) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value instanceof Number seconds) {
            return Instant.ofEpochSecond(seconds.longValue());
        }
        return null;
    }
}
