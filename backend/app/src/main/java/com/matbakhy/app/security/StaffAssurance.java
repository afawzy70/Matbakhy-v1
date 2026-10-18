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
    /** When an administrative MFA reset last happened for the account (D18); proofs older than that do not count. */
    private final java.util.function.Function<java.util.UUID, Instant> resetAt;

    @org.springframework.beans.factory.annotation.Autowired
    StaffAssurance(IdentityProperties properties, Clock clock, org.springframework.jdbc.core.JdbcTemplate jdbc) {
        this(properties, clock, id -> jdbc.query("SELECT reset_at FROM user_mfa_reset WHERE user_id = ?",
                (rs, n) -> rs.getTimestamp(1).toInstant(), id).stream().findFirst().orElse(null));
    }

    StaffAssurance(IdentityProperties properties, Clock clock, java.util.function.Function<java.util.UUID, Instant> resetAt) {
        this.properties = properties;
        this.clock = clock;
        this.resetAt = resetAt;
    }

    StaffAssurance(IdentityProperties properties, Clock clock) {
        this(properties, clock, id -> null);
    }

    /** True when the provider asserted an MFA-level ACR or an MFA method in amr (no recency check). */
    public static boolean assertsMfa(java.util.Map<String, Object> claims, java.util.List<String> mfaAcrValues) {
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
        // A proof made before the account's last administrative MFA reset no longer counts, for any policy mode.
        Instant reset = mfa && recent ? resetAt.apply(account.getPrincipal().id()) : null;
        boolean afterReset = reset == null || !authTime.isBefore(reset);
        if (!(mfa && recent && afterReset)) {
            throw new StepUpRequiredException();
        }
        return true;
    }

    /** The provider-asserted authentication time, if present and well formed. */
    public static Instant authTime(java.util.Map<String, Object> claims) {
        return toInstant(claims.get("auth_time"));
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
