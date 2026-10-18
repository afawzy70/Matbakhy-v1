package com.matbakhy.app.mfa;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestKitchens;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D18 policy rules against PostgreSQL through the real security chain: preview defaults, administrator control per category,
 * precedence over user preference, role-based categories, step-up for weakening changes, and the reset guards.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class MfaPolicyIntegrationTest {

    private static final String PROTECTED = "/api/v1/me/addresses";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;

    String adminSubject;
    UUID admin;

    @BeforeEach
    void setUp() {
        adminSubject = UUID.randomUUID().toString();
        admin = logins.login(adminSubject, "Admin").getPrincipal().id();
        resetPolicies();
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'security.mfa_policy', 'test'), (?, ?, 'security.mfa_reset', 'test')",
                UUID.randomUUID(), admin, UUID.randomUUID(), admin);
    }

    @AfterEach
    void resetPolicies() {
        jdbc.update("UPDATE mfa_policy SET mode = 'OPTIONAL', reason = NULL, version = version + 1");
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private AccountAuthentication plain(String subject) {
        return logins.login(subject, "User");
    }

    private AccountAuthentication mfa(String subject) {
        return logins.loginWithMfa(subject, "User");
    }

    private long version(String category) {
        return jdbc.queryForObject("SELECT version FROM mfa_policy WHERE category = ?", Long.class, category);
    }

    private MockHttpServletResponse setPolicy(String category, String mode) throws Exception {
        return call(put("/api/v1/ops/security/mfa-policy/" + category).content("{\"mode\":\"" + mode + "\",\"expectedVersion\":" + version(category)
                + ",\"reason\":\"owner decision test\"}"), mfa(adminSubject));
    }

    private MockHttpServletResponse setPreference(AccountAuthentication who, boolean enabled) throws Exception {
        return call(put("/api/v1/me/security/mfa").content("{\"enabled\":" + enabled + "}"), who);
    }

    private int protectedStatus(AccountAuthentication who) throws Exception {
        return call(get(PROTECTED), who).getStatus();
    }

    @Test
    void the_preview_defaults_are_optional_for_every_category_and_nobody_is_forced() throws Exception {
        JsonNode policies = ok(call(get("/api/v1/ops/security/mfa-policy"), plain(adminSubject)), 200);
        assertThat(policies.get("items")).hasSize(3);
        policies.get("items").forEach(p -> assertThat(p.get("mode").asString()).isEqualTo("OPTIONAL"));
        String customer = UUID.randomUUID().toString();
        assertThat(protectedStatus(plain(customer))).isEqualTo(200);
        JsonNode state = ok(call(get("/api/v1/me/security"), plain(customer)), 200);
        assertThat(state.get("required").asBoolean()).isFalse();
        assertThat(state.get("available").asBoolean()).isTrue();
        assertThat(state.get("effectiveMode").asString()).isEqualTo("OPTIONAL");
    }

    @Test
    void only_the_policy_permission_with_a_recent_second_factor_can_change_a_policy() throws Exception {
        String body = "{\"mode\":\"REQUIRED\",\"expectedVersion\":" + version("CUSTOMER") + ",\"reason\":\"owner decision test\"}";
        assertThat(call(put("/api/v1/ops/security/mfa-policy/CUSTOMER").content(body), plain(UUID.randomUUID().toString())).getStatus())
                .as("no permission").isEqualTo(403);
        MockHttpServletResponse weak = call(put("/api/v1/ops/security/mfa-policy/CUSTOMER").content(body), plain(adminSubject));
        assertThat(weak.getStatus()).isEqualTo(403);
        assertThat(weak.getContentAsString()).contains("STEP_UP_REQUIRED");
        assertThat(call(put("/api/v1/ops/security/mfa-policy/CUSTOMER").content(body.replace("owner decision test", "x")), mfa(adminSubject)).getStatus())
                .as("reason too short").isEqualTo(422);
        assertThat(call(put("/api/v1/ops/security/mfa-policy/CUSTOMER").content(body.replace("\"expectedVersion\":" + version("CUSTOMER"), "\"expectedVersion\":999")),
                mfa(adminSubject)).getStatus()).as("stale version").isEqualTo(409);

        JsonNode changed = ok(call(put("/api/v1/ops/security/mfa-policy/CUSTOMER").content(body), mfa(adminSubject)), 200);
        assertThat(changed.get("mode").asString()).isEqualTo("REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'mfa.policy.update' AND actor_id = ? AND outcome = 'ALLOWED'",
                Integer.class, admin)).isEqualTo(1);
    }

    @Test
    void a_required_policy_blocks_a_session_without_the_second_factor_everywhere_but_the_settings_endpoints() throws Exception {
        String user = UUID.randomUUID().toString();
        ok(setPolicy("CUSTOMER", "REQUIRED"), 200);

        MockHttpServletResponse refused = call(get(PROTECTED), plain(user));
        assertThat(refused.getStatus()).isEqualTo(403);
        assertThat(refused.getContentAsString()).contains("STEP_UP_REQUIRED");
        assertThat(call(get("/api/v1/me"), plain(user)).getStatus()).as("profile stays reachable").isEqualTo(200);
        JsonNode state = ok(call(get("/api/v1/me/security"), plain(user)), 200);
        assertThat(state.get("required").asBoolean()).isTrue();
        assertThat(state.get("verifiedInSession").asBoolean()).isFalse();
        assertThat(call(get("/api/v1/zones"), plain(user)).getStatus()).as("public reads stay public").isEqualTo(200);

        assertThat(protectedStatus(mfa(user))).isEqualTo(200);
        assertThat(ok(call(get("/api/v1/me/security"), mfa(user)), 200).get("verifiedInSession").asBoolean()).isTrue();
    }

    @Test
    void the_users_preference_can_never_override_a_required_policy() throws Exception {
        String user = UUID.randomUUID().toString();
        ok(setPolicy("CUSTOMER", "REQUIRED"), 200);
        MockHttpServletResponse off = setPreference(mfa(user), false);
        assertThat(off.getStatus()).isEqualTo(409);
        assertThat(off.getContentAsString()).contains("MFA_REQUIRED_BY_POLICY");
        assertThat(protectedStatus(plain(user))).as("still enforced after the attempt").isEqualTo(403);
    }

    @Test
    void under_optional_a_user_can_enable_mfa_only_after_proving_it_and_can_disable_only_after_a_recent_second_factor() throws Exception {
        String user = UUID.randomUUID().toString();
        MockHttpServletResponse unproven = setPreference(plain(user), true);
        assertThat(unproven.getStatus()).as("enabling needs a session that passed the second factor").isEqualTo(403);
        assertThat(unproven.getContentAsString()).contains("STEP_UP_REQUIRED");

        assertThat(ok(setPreference(mfa(user), true), 200).get("enabled").asBoolean()).isTrue();
        assertThat(ok(setPreference(mfa(user), true), 200).get("enabled").asBoolean()).as("idempotent").isTrue();
        assertThat(protectedStatus(plain(user))).as("the user's own choice now needs the factor").isEqualTo(403);
        assertThat(protectedStatus(mfa(user))).isEqualTo(200);

        MockHttpServletResponse noFactor = setPreference(plain(user), false);
        assertThat(noFactor.getStatus()).isEqualTo(403);
        MockHttpServletResponse stale = setPreference(logins.loginWithMfaAt(user, "User", Instant.now().minusSeconds(3600)), false);
        assertThat(stale.getStatus()).as("a factor proven an hour ago is not recent reauthentication").isEqualTo(403);
        assertThat(stale.getContentAsString()).contains("STEP_UP_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT enabled FROM user_mfa_preference WHERE user_id = ?", Boolean.class, plain(user).getPrincipal().id())).isTrue();

        assertThat(ok(setPreference(mfa(user), false), 200).get("enabled").asBoolean()).isFalse();
        assertThat(protectedStatus(plain(user))).as("back to the default").isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND action IN ('mfa.preference.enable', 'mfa.preference.disable')",
                Integer.class, plain(user).getPrincipal().id())).isEqualTo(2);
    }

    @Test
    void a_disabled_policy_offers_no_enrolment_and_does_not_silently_remove_an_existing_preference() throws Exception {
        String user = UUID.randomUUID().toString();
        UUID account = plain(user).getPrincipal().id();
        jdbc.update("INSERT INTO user_mfa_preference (user_id, enabled) VALUES (?, true)", account);
        ok(setPolicy("CUSTOMER", "DISABLED"), 200);

        MockHttpServletResponse enrol = setPreference(mfa(user), true);
        assertThat(enrol.getStatus()).isEqualTo(409);
        assertThat(enrol.getContentAsString()).contains("MFA_UNAVAILABLE");
        JsonNode state = ok(call(get("/api/v1/me/security"), plain(user)), 200);
        assertThat(state.get("available").asBoolean()).isFalse();
        assertThat(state.get("enabled").asBoolean()).as("stored choice is kept, not erased").isTrue();
        assertThat(protectedStatus(plain(user))).as("not required while DISABLED; password remains required by login itself").isEqualTo(200);

        ok(setPolicy("CUSTOMER", "OPTIONAL"), 200);
        assertThat(protectedStatus(plain(user))).as("the earlier choice applies again").isEqualTo(403);
    }

    @Test
    void category_policies_follow_the_account_role_and_the_most_protective_one_applies() throws Exception {
        String staffSubject = UUID.randomUUID().toString();
        UUID staff = plain(staffSubject).getPrincipal().id();
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'audit.read', 'test')", UUID.randomUUID(), staff);
        String cookSubject = UUID.randomUUID().toString();
        UUID cook = plain(cookSubject).getPrincipal().id();
        TestKitchens.approvedKitchen(jdbc, cook, logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        String customerSubject = UUID.randomUUID().toString();

        ok(setPolicy("STAFF", "REQUIRED"), 200);
        assertThat(protectedStatus(plain(staffSubject))).as("staff category required").isEqualTo(403);
        assertThat(protectedStatus(mfa(staffSubject))).isEqualTo(200);
        assertThat(protectedStatus(plain(cookSubject))).as("kitchen category still optional").isEqualTo(200);
        assertThat(protectedStatus(plain(customerSubject))).isEqualTo(200);

        ok(setPolicy("KITCHEN", "REQUIRED"), 200);
        assertThat(protectedStatus(plain(cookSubject))).isEqualTo(403);
        assertThat(protectedStatus(plain(customerSubject))).as("customers are unaffected by other categories").isEqualTo(200);
        ok(setPolicy("STAFF", "OPTIONAL"), 200);
        assertThat(protectedStatus(plain(staffSubject))).isEqualTo(200);
        JsonNode cookState = ok(call(get("/api/v1/me/security"), plain(cookSubject)), 200);
        assertThat(cookState.get("categories").toString()).contains("KITCHEN").contains("CUSTOMER");
    }

    @Test
    void the_reset_is_guarded_by_permission_a_recent_factor_a_reason_and_never_applies_to_oneself() throws Exception {
        String targetSubject = UUID.randomUUID().toString();
        UUID target = plain(targetSubject).getPrincipal().id();
        String body = "{\"reason\":\"verified by support case 1234\"}";
        assertThat(call(post("/api/v1/ops/accounts/" + target + "/mfa-reset").content(body), plain(UUID.randomUUID().toString())).getStatus()).isEqualTo(403);
        MockHttpServletResponse weak = call(post("/api/v1/ops/accounts/" + target + "/mfa-reset").content(body), plain(adminSubject));
        assertThat(weak.getStatus()).isEqualTo(403);
        assertThat(weak.getContentAsString()).contains("STEP_UP_REQUIRED");
        assertThat(call(post("/api/v1/ops/accounts/" + target + "/mfa-reset").content("{\"reason\":\"short\"}"), mfa(adminSubject)).getStatus()).isEqualTo(422);
        assertThat(call(post("/api/v1/ops/accounts/" + admin + "/mfa-reset").content(body), mfa(adminSubject)).getStatus()).as("self reset").isEqualTo(403);
        assertThat(call(post("/api/v1/ops/accounts/" + UUID.randomUUID() + "/mfa-reset").content(body), mfa(adminSubject)).getStatus()).isEqualTo(404);
        MockHttpServletResponse unavailable = call(post("/api/v1/ops/accounts/" + target + "/mfa-reset").content(body), mfa(adminSubject));
        assertThat(unavailable.getStatus()).as("no provider admin access in this context").isEqualTo(503);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'mfa.reset' AND actor_id = ? AND outcome = 'DENIED'", Integer.class, admin))
                .isEqualTo(1);
    }

    @Test
    void enforcement_and_settings_are_for_signed_in_accounts_only() throws Exception {
        assertThat(mvc.perform(get("/api/v1/me/security")).andReturn().getResponse().getStatus()).isEqualTo(401);
    }
}
