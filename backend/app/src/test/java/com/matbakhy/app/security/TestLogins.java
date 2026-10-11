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
}
