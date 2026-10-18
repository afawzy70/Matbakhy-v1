package com.matbakhy.app.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The recovery reset against an identity provider double that fails part way: the half-done act is audited, earlier assertions are
 * already invalidated, and the application's own web sessions of the account end. Provider behaviour itself is covered by
 * MfaResetIntegrationTest against real Keycloak.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({TestLogins.class, MfaResetAuditIntegrationTest.Doubles.class})
class MfaResetAuditIntegrationTest {

    /** Two OTP factors; the second removal fails. */
    static final class FlakyAdmin implements IdentityProviderAdmin {
        final List<String> removed = new ArrayList<>();
        boolean failSecond = true;
        boolean loggedOut;

        @Override public boolean configured() { return true; }
        @Override public List<String> otpCredentialIds(String subject) { return List.of("c1", "c2"); }

        @Override
        public void deleteCredential(String subject, String credentialId) {
            if (failSecond && credentialId.equals("c2")) {
                throw new IllegalStateException("provider outage");
            }
            removed.add(credentialId);
        }

        @Override public void logout(String subject) { loggedOut = true; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class Doubles {
        @Bean
        @Primary
        FlakyAdmin flakyAdmin() {
            return new FlakyAdmin();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired FlakyAdmin provider;

    String adminSubject;
    UUID admin;

    @BeforeEach
    void setUp() {
        adminSubject = UUID.randomUUID().toString();
        admin = logins.login(adminSubject, "Admin").getPrincipal().id();
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'security.mfa_reset', 'test')", UUID.randomUUID(), admin);
        provider.removed.clear();
        provider.loggedOut = false;
        provider.failSecond = true;
    }

    private MockHttpServletResponse reset(UUID target) throws Exception {
        return mvc.perform(post("/api/v1/ops/accounts/" + target + "/mfa-reset").content("{\"reason\":\"verified by support case 77\"}")
                .with(authentication(logins.loginWithMfa(adminSubject, "Admin"))).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    @Test
    void a_reset_that_stops_half_way_is_audited_with_what_was_done_and_still_invalidates_earlier_assertions() throws Exception {
        String targetSubject = UUID.randomUUID().toString();
        UUID target = logins.login(targetSubject, "Target").getPrincipal().id();
        jdbc.update("INSERT INTO spring_session (primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time, principal_name) "
                + "VALUES (?, ?, 1, 1, 1800, 99999999999999, ?)", UUID.randomUUID().toString(), UUID.randomUUID().toString(), targetSubject);

        MockHttpServletResponse failed = reset(target);
        assertThat(failed.getStatus()).isGreaterThanOrEqualTo(500);
        assertThat(provider.removed).containsExactly("c1");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_mfa_reset WHERE user_id = ?", Integer.class, target))
                .as("earlier proofs stop counting even though the reset did not finish").isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'mfa.reset' AND object_id = ? AND outcome = 'DENIED' AND reason LIKE 'reset incomplete%'",
                Integer.class, target.toString())).isEqualTo(1);

        provider.failSecond = false;
        MockHttpServletResponse done = reset(target);
        assertThat(done.getStatus()).isEqualTo(200);
        assertThat(done.getContentAsString()).contains("\"removedFactors\":2");
        assertThat(provider.loggedOut).isTrue();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = ?", Integer.class, targetSubject)).as("application sessions ended").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'mfa.reset' AND object_id = ? AND outcome = 'ALLOWED'",
                Integer.class, target.toString())).isEqualTo(1);
    }
}
