package com.matbakhy.app.security;

import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Test helper: builds the same {@link AccountAuthentication} the production mapper produces for a
 * validated provider identity. Used where provider login itself is not under test (MB-009 covers it
 * against real Keycloak).
 */
@Component
public class TestLogins {

    public static final String ISSUER = "https://idp.test.invalid/realms/matbakhy-test";
    private final AccountMapper mapper;

    TestLogins(AccountMapper mapper) {
        this.mapper = mapper;
    }

    public AccountAuthentication newCustomer(String name) {
        return login(UUID.randomUUID().toString(), name);
    }

    public AccountAuthentication login(String subject, String name) {
        return mapper.map(ISSUER, subject, Map.of("email_verified", true, "name", name));
    }

    /** A login whose provider claims also carry an email address. */
    public AccountAuthentication loginWithEmail(String subject, String name, String email) {
        return mapper.map(ISSUER, subject, Map.of("email_verified", true, "name", name, "email", email));
    }

    /** Same identity, with provider-asserted recent MFA (acr=2, auth_time=now) as a step-up login would carry. */
    public AccountAuthentication loginWithMfa(String subject, String name) {
        return mapper.map(ISSUER, subject, Map.of("email_verified", true, "name", name, "acr", "2",
                "auth_time", java.time.Instant.now().getEpochSecond()));
    }

    /** MFA asserted by the provider, but authenticated at {@code authTime} (to test "recent" step-up windows). */
    public AccountAuthentication loginWithMfaAt(String subject, String name, java.time.Instant authTime) {
        return mapper.map(ISSUER, subject, Map.of("email_verified", true, "name", name, "acr", "2", "auth_time", authTime.getEpochSecond()));
    }
}
