package com.matbakhy.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.KeycloakTestServer;
import com.matbakhy.app.support.MfaBrowser;
import com.matbakhy.app.support.MfaBrowser.Outcome;
import com.matbakhy.app.support.PostgresTestConfiguration;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D18 end to end with real Keycloak, PostgreSQL and the real security chain over bearer tokens: the server-side policy refuses a
 * session without the second factor, accepts one that passed step-up, and the audited administrative reset removes a user's
 * OTP credential at the provider without ever returning a secret.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = "matbakhy.environment=CI")
@Import({PostgresTestConfiguration.class})
class MfaResetIntegrationTest {

    private static final String PW = KeycloakTestServer.MFA_PASSWORD;
    private static final String SECRET = KeycloakTestServer.MFA_TOTP_SECRET;
    private final JsonMapper json = new JsonMapper();

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        KeycloakTestServer.springProperties().forEach((k, v) -> registry.add(k, () -> v));
    }

    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void restoreOptional() {
        jdbc.update("UPDATE mfa_policy SET mode = 'OPTIONAL', version = version + 1");
    }

    private static String api(String path) {
        return "http://localhost:" + KeycloakTestServer.APP_PORT + path;
    }

    private HttpResponse<String> send(String method, String path, String token, String body) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api(path))).header("Authorization", "Bearer " + token);
        if (body != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return java.net.http.HttpClient.newHttpClient().send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String plainToken(String user) throws Exception {
        Outcome o = MfaBrowser.login(user, PW, null, false);
        assertThat(o.issuedCode()).isTrue();
        return MfaBrowser.accessToken(o);
    }

    private String mfaToken(String user) throws Exception {
        Outcome o = MfaBrowser.login(user, PW, MfaBrowser.now(SECRET), true);
        assertThat(o.issuedCode()).isTrue();
        return MfaBrowser.accessToken(o);
    }

    private UUID accountId(String token) throws Exception {
        HttpResponse<String> me = send("GET", "/api/v1/me", token, null);
        assertThat(me.statusCode()).as(me.body()).isEqualTo(200);
        return UUID.fromString(json.readTree(me.body()).get("id").asString());
    }

    private void grant(UUID account, String permission) {
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'mfa integration test')", UUID.randomUUID(), account, permission);
    }

    @Test
    void with_a_required_policy_a_session_without_the_second_factor_is_refused_and_a_stepped_up_one_is_accepted() throws Exception {
        String plain = plainToken("otp-policy");
        assertThat(send("GET", "/api/v1/me/addresses", plain, null).statusCode()).as("OPTIONAL by default").isEqualTo(200);

        jdbc.update("UPDATE mfa_policy SET mode = 'REQUIRED', version = version + 1 WHERE category = 'CUSTOMER'");
        HttpResponse<String> refused = send("GET", "/api/v1/me/addresses", plain, null);
        assertThat(refused.statusCode()).isEqualTo(403);
        assertThat(refused.body()).contains("STEP_UP_REQUIRED");
        assertThat(send("GET", "/api/v1/me", plain, null).statusCode()).as("profile stays reachable to start enrolment").isEqualTo(200);
        JsonNode state = json.readTree(send("GET", "/api/v1/me/security", plain, null).body());
        assertThat(state.get("required").asBoolean()).isTrue();
        assertThat(state.get("verifiedInSession").asBoolean()).isFalse();

        String stepped = mfaToken("otp-policy");
        assertThat(send("GET", "/api/v1/me/addresses", stepped, null).statusCode()).isEqualTo(200);
        assertThat(json.readTree(send("GET", "/api/v1/me/security", stepped, null).body()).get("verifiedInSession").asBoolean()).isTrue();
    }

    @Test
    void the_audited_reset_removes_the_otp_factor_at_the_provider_ends_sessions_and_returns_no_secret() throws Exception {
        String admin = mfaToken("otp-admin");
        UUID adminId = accountId(admin);
        grant(adminId, "security.mfa_reset");
        String targetPlain = plainToken("otp-reset");
        UUID target = accountId(targetPlain);

        assertThat(send("POST", "/api/v1/ops/accounts/" + adminId + "/mfa-reset", admin, "{\"reason\":\"trying my own account\"}").statusCode())
                .as("self reset refused").isEqualTo(403);
        assertThat(send("POST", "/api/v1/ops/accounts/" + target + "/mfa-reset", targetPlain, "{\"reason\":\"not an administrator at all\"}").statusCode())
                .as("no permission").isEqualTo(403);
        assertThat(send("POST", "/api/v1/ops/accounts/" + target + "/mfa-reset", admin, "{\"reason\":\"short\"}").statusCode()).as("reason too short").isEqualTo(422);

        HttpResponse<String> reset = send("POST", "/api/v1/ops/accounts/" + target + "/mfa-reset", admin, "{\"reason\":\"lost phone, identity verified by support\"}");
        assertThat(reset.statusCode()).as(reset.body()).isEqualTo(200);
        JsonNode result = json.readTree(reset.body());
        assertThat(result.get("removedFactors").asInt()).isEqualTo(1);
        assertThat(result.get("sessionsEnded").asBoolean()).isTrue();
        assertThat(reset.body().toLowerCase()).doesNotContain("secret", "totp");

        // The factor is gone at the provider: step-up now leads to enrolment instead of an OTP prompt.
        Outcome after = MfaBrowser.login("otp-reset", PW, null, true);
        assertThat(after.issuedCode()).isFalse();
        assertThat(after.asksForEnrolment()).isTrue();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'mfa.reset' AND object_id = ? AND outcome = 'ALLOWED' AND reason LIKE 'lost phone%'",
                Integer.class, target.toString())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'mfa.reset' AND actor_id = ? AND outcome = 'DENIED'",
                Integer.class, adminId)).isGreaterThanOrEqualTo(1);

        HttpResponse<String> again = send("POST", "/api/v1/ops/accounts/" + target + "/mfa-reset", admin, "{\"reason\":\"second reset is a harmless no-op\"}");
        assertThat(json.readTree(again.body()).get("removedFactors").asInt()).isZero();
    }

    @Test
    void a_reset_needs_a_recent_second_factor_from_the_administrator() throws Exception {
        String weakAdmin = plainToken("otp-admin");
        UUID adminId = accountId(weakAdmin);
        grant(adminId, "security.mfa_reset");
        HttpResponse<String> r = send("POST", "/api/v1/ops/accounts/" + UUID.randomUUID() + "/mfa-reset", weakAdmin, "{\"reason\":\"password-only administrator\"}");
        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).contains("STEP_UP_REQUIRED");
    }
}
