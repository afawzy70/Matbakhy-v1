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

    public boolean recentMfa(Authentication authentication) {
        if (!(authentication instanceof AccountAuthentication account) || !account.getPrincipal().active()) {
            return false;
        }
        var claims = account.identityClaims();
        boolean mfa = claims.get("acr") != null && properties.mfaAcrValues().contains(claims.get("acr").toString());
        if (!mfa && claims.get("amr") instanceof Collection<?> methods) {
            mfa = methods.stream().map(Object::toString).anyMatch(m -> m.equals("mfa") || m.equals("otp") || m.equals("hwk"));
        }
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
