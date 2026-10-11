package com.matbakhy.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.KeycloakTestServer;
import com.matbakhy.app.support.MutableClock;
import com.matbakhy.app.support.OidcBrowser;
import com.matbakhy.app.support.PostgresTestConfiguration;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * TC-001, TC-002, TC-109 against real Keycloak and PostgreSQL: anonymous/expired/suspended denial,
 * PKCE contract, CSRF on web mutations, logout revoking only the intended session, forged or foreign
 * tokens rejected and role claims granting nothing.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = "matbakhy.environment=CI")
@Import({PostgresTestConfiguration.class, IdentityIntegrationTest.TestBeans.class})
class IdentityIntegrationTest {

    private static final String MOBILE_REDIRECT = "matbakhy-dev://auth/callback";
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    @DynamicPropertySource
    static void identity(DynamicPropertyRegistry registry) {
        KeycloakTestServer.springProperties().forEach((k, v) -> registry.add(k, () -> v));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }

        @Bean
        StaffProbe staffProbe() {
            return new StaffProbe();
        }
    }

    /** Test-only endpoints standing in for staff operations delivered in MB-011+. */
    @RestController
    static class StaffProbe {
        @GetMapping("/api/v1/test-staff/grants")
        @PreAuthorize("hasAuthority('PERM_staff.grant')")
        String grants() {
            return "staff data";
        }

        @GetMapping("/api/v1/test-staff/step-up")
        @PreAuthorize("@staffAssurance.recentMfa(authentication)")
        String stepUp() {
            return "high-risk data";
        }
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    Clock clock;

    @AfterEach
    void resetClock() {
        ((MutableClock) clock).reset();
    }

    private String api(String path) {
        return "http://localhost:" + KeycloakTestServer.APP_PORT + path;
    }

    private static String authorizeUrl(String clientId, String redirect, String challenge, String state) {
        String url = KeycloakTestServer.issuer() + "/protocol/openid-connect/auth?response_type=code&scope=openid"
                + "&client_id=" + clientId + "&redirect_uri=" + OidcBrowser.enc(redirect) + "&state=" + state;
        return challenge == null ? url : url + "&code_challenge=" + challenge + "&code_challenge_method=S256";
    }

    /** Authorization code + PKCE as a native app would do it; returns the token endpoint response. */
    private HttpResponse<String> nativeLogin(String clientId, String redirect, String user, String password,
                                             boolean wrongVerifier) throws Exception {
        OidcBrowser browser = new OidcBrowser();
        String verifier = OidcBrowser.randomVerifier();
        String state = UUID.randomUUID().toString();
        String callback = browser.login(authorizeUrl(clientId, redirect, OidcBrowser.challenge(verifier), state), user, password);
        assertThat(callback).startsWith(redirect);
        assertThat(OidcBrowser.queryParam(callback, "state")).isEqualTo(state);
        String code = OidcBrowser.queryParam(callback, "code");
        String form = "grant_type=authorization_code&client_id=" + clientId + "&redirect_uri=" + OidcBrowser.enc(redirect)
                + "&code=" + OidcBrowser.enc(code) + "&code_verifier=" + (wrongVerifier ? OidcBrowser.randomVerifier() : verifier);
        return browser.send(HttpRequest.newBuilder(URI.create(KeycloakTestServer.issuer() + "/protocol/openid-connect/token"))
                .header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form)).build());
    }

    private String nativeToken(String clientId, String redirect, String user, String password) throws Exception {
        HttpResponse<String> response = nativeLogin(clientId, redirect, user, password, false);
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        Matcher m = ACCESS_TOKEN.matcher(response.body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private HttpResponse<String> bearer(String path, String token) throws Exception {
        return new OidcBrowser().send(HttpRequest.newBuilder(URI.create(api(path))).header("Authorization", "Bearer " + token).GET().build());
    }

    /** Full web login through the backend's OIDC client; returns a browser holding the session cookie. */
    private OidcBrowser webLogin(String user, String password) throws Exception {
        OidcBrowser browser = new OidcBrowser();
        String authorize = browser.followUntil(api("/oauth2/authorization/keycloak"), KeycloakTestServer.issuer());
        assertThat(OidcBrowser.queryParam(authorize, "code_challenge")).as("backend uses PKCE too").isNotNull();
        String callback = browser.login(authorize, user, password);
        String landing = browser.followUntil(callback, "http://localhost:5173/");
        assertThat(landing).isEqualTo("http://localhost:5173/");
        assertThat(browser.cookie("MATBAKHY_SESSION")).isPresent();
        return browser;
    }

    private HttpResponse<String> webGet(OidcBrowser browser, String path) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(api(path))).GET().build());
    }

    private HttpResponse<String> webPatchMe(OidcBrowser browser, String json, boolean withCsrf) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(api("/api/v1/me")))
                .header("Content-Type", "application/json").method("PATCH", HttpRequest.BodyPublishers.ofString(json));
        if (withCsrf) {
            b.header("X-XSRF-TOKEN", browser.cookie("XSRF-TOKEN").orElseThrow());
        }
        return browser.send(b.build());
    }

    private static long version(String body) {
        Matcher m = Pattern.compile("\"version\":(\\d+)").matcher(body);
        assertThat(m.find()).isTrue();
        return Long.parseLong(m.group(1));
    }

    // ---- TC-001: anonymous, suspended and public access ----

    @Test
    void anonymous_private_calls_get_401_without_private_data() throws Exception {
        HttpResponse<String> me = new OidcBrowser().get(api("/api/v1/me"));
        assertThat(me.statusCode()).isEqualTo(401);
        assertThat(me.body()).contains("\"code\":\"UNAUTHENTICATED\"").doesNotContain("displayName");
        assertThat(me.headers().firstValue("Location")).as("API never redirects to a login page").isEmpty();
    }

    @Test
    void public_browsing_stays_anonymous() throws Exception {
        HttpResponse<String> meals = new OidcBrowser().get(api("/api/v1/meals"));
        assertThat(meals.statusCode()).as("public route is not behind authentication (not implemented yet => 404)").isEqualTo(404);
    }

    @Test
    void suspended_account_is_denied_with_scoped_error_and_no_profile() throws Exception {
        String token = nativeToken("matbakhy-mobile", MOBILE_REDIRECT, "customer-b", KeycloakTestServer.PASSWORD_B);
        assertThat(bearer("/api/v1/me", token).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE app_user SET status = 'SUSPENDED' WHERE subject = (SELECT subject FROM app_user WHERE display_name = 'Customer SyntheticB' LIMIT 1)");
        HttpResponse<String> denied = bearer("/api/v1/me", token);
        assertThat(denied.statusCode()).isEqualTo(403);
        assertThat(denied.body()).contains("\"code\":\"ACCOUNT_DISABLED\"").doesNotContain("SyntheticB");
        jdbc.update("UPDATE app_user SET status = 'ACTIVE' WHERE display_name = 'Customer SyntheticB'");
    }

    // ---- TC-002: native PKCE contract ----

    @Test
    void native_authorization_without_pkce_is_refused_by_the_provider() throws Exception {
        HttpResponse<String> r = new OidcBrowser().get(authorizeUrl("matbakhy-mobile", MOBILE_REDIRECT, null, "s1"));
        String location = r.headers().firstValue("Location").orElse("");
        assertThat(r.statusCode()).isEqualTo(302);
        assertThat(OidcBrowser.queryParam(location, "error")).isEqualTo("invalid_request");
        assertThat(OidcBrowser.queryParam(location, "code")).isNull();
    }

    @Test
    void native_redirect_outside_the_allowlist_is_refused() throws Exception {
        HttpResponse<String> r = new OidcBrowser().get(authorizeUrl("matbakhy-mobile", "https://attacker.invalid/cb",
                OidcBrowser.challenge(OidcBrowser.randomVerifier()), "s2"));
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.headers().firstValue("Location")).isEmpty();
    }

    @Test
    void code_exchange_with_wrong_pkce_verifier_fails() throws Exception {
        HttpResponse<String> r = nativeLogin("matbakhy-mobile", MOBILE_REDIRECT, "customer-a", KeycloakTestServer.PASSWORD_A, true);
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("invalid_grant").doesNotContain("access_token");
    }

    // ---- TC-109: bearer validation, forged claims, expiry ----

    @Test
    void valid_native_token_maps_to_one_local_account() throws Exception {
        String token = nativeToken("matbakhy-mobile", MOBILE_REDIRECT, "customer-a", KeycloakTestServer.PASSWORD_A);
        HttpResponse<String> first = bearer("/api/v1/me", token);
        HttpResponse<String> second = bearer("/api/v1/me", token);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"emailVerified\":true", "\"capabilities\":[\"BROWSE\",\"ORDER\"]");
        assertThat(second.body()).isEqualTo(first.body());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM app_user WHERE issuer = ?", Integer.class,
                KeycloakTestServer.issuer())).isGreaterThanOrEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM (SELECT issuer, subject FROM app_user GROUP BY 1, 2 HAVING count(*) > 1) d",
                Integer.class)).isZero();
    }

    @Test
    void provider_role_claims_grant_no_staff_permission() throws Exception {
        String token = nativeToken("matbakhy-mobile", MOBILE_REDIRECT, "customer-a", KeycloakTestServer.PASSWORD_A);
        String payload = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8);
        assertThat(payload).as("the provider really asserts the admin role").contains("admin");
        HttpResponse<String> r = bearer("/api/v1/test-staff/grants", token);
        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).contains("\"code\":\"FORBIDDEN\"").doesNotContain("staff data");
    }

    @Test
    void staff_operations_require_recent_mfa_assurance() throws Exception {
        String token = nativeToken("matbakhy-mobile", MOBILE_REDIRECT, "customer-a", KeycloakTestServer.PASSWORD_A);
        HttpResponse<String> r = bearer("/api/v1/test-staff/step-up", token);
        assertThat(r.statusCode()).isEqualTo(403);
        assertThat(r.body()).contains("\"code\":\"STEP_UP_REQUIRED\"").doesNotContain("high-risk data");
    }

    @Test
    void reused_token_loses_staff_access_once_the_grant_is_revoked() throws Exception {
        String token = nativeToken("matbakhy-mobile", MOBILE_REDIRECT, "customer-a", KeycloakTestServer.PASSWORD_A);
        assertThat(bearer("/api/v1/me", token).statusCode()).isEqualTo(200);
        String subject = new String(Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8)
                .replaceAll(".*\"sub\":\"([^\"]+)\".*", "$1");
        UUID account = jdbc.queryForObject("SELECT id FROM app_user WHERE issuer = ? AND subject = ?", UUID.class,
                KeycloakTestServer.issuer(), subject);
        UUID grant = UUID.randomUUID();
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'audit.read', 'temporary duty')", grant, account);
        assertThat(bearer("/api/v1/ops/audit", token).statusCode()).isEqualTo(200);
        jdbc.update("UPDATE user_grant SET revoked_at = now(), revoked_by = user_id, revoke_reason = 'duty ended' WHERE id = ?", grant);
        HttpResponse<String> reused = bearer("/api/v1/ops/audit", token);
        assertThat(reused.statusCode()).as("same unexpired token, grant revoked").isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND outcome = 'DENIED'", Integer.class, account))
                .isGreaterThanOrEqualTo(1);
    }

    @Test
    void token_for_another_client_audience_is_rejected() throws Exception {
        String token = nativeToken("unrelated-app", "unrelated-app://callback", "customer-a", KeycloakTestServer.PASSWORD_A);
        assertThat(bearer("/api/v1/me", token).statusCode()).isEqualTo(401);
    }

    @Test
    void tampered_and_self_signed_tokens_are_rejected() throws Exception {
        String token = nativeToken("matbakhy-mobile", MOBILE_REDIRECT, "customer-a", KeycloakTestServer.PASSWORD_A);
        String[] parts = token.split("\\.");
        String claims = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                claims.replace("\"email_verified\":true", "\"email_verified\":false").getBytes(StandardCharsets.UTF_8));
        assertThat(bearer("/api/v1/me", parts[0] + "." + forgedPayload + "." + parts[2]).statusCode()).isEqualTo(401);

        RSAKey attacker = new RSAKeyGenerator(2048).keyID("attacker").generate();
        SignedJWT selfSigned = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("attacker").build(),
                new JWTClaimsSet.Builder().issuer(KeycloakTestServer.issuer()).subject(UUID.randomUUID().toString())
                        .audience(List.of("matbakhy-api")).expirationTime(new Date(System.currentTimeMillis() + 300_000))
                        .issueTime(new Date()).claim("realm_access", Map.of("roles", List.of("admin"))).build());
        selfSigned.sign(new RSASSASigner(attacker));
        assertThat(bearer("/api/v1/me", selfSigned.serialize()).statusCode()).isEqualTo(401);
        assertThat(bearer("/api/v1/me", "not-a-jwt").statusCode()).isEqualTo(401);
    }

    @Test
    void expired_token_is_rejected() throws Exception {
        String token = nativeToken("matbakhy-mobile", MOBILE_REDIRECT, "customer-a", KeycloakTestServer.PASSWORD_A);
        assertThat(bearer("/api/v1/me", token).statusCode()).isEqualTo(200);
        ((MutableClock) clock).advance(Duration.ofHours(1));
        HttpResponse<String> expired = bearer("/api/v1/me", token);
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }

    // ---- TC-002 / TC-109: web session, CSRF, CORS, logout, expiry ----

    @Test
    void web_login_creates_server_session_and_mutations_require_csrf() throws Exception {
        OidcBrowser browser = webLogin("customer-a", KeycloakTestServer.PASSWORD_A);
        HttpResponse<String> me = webGet(browser, "/api/v1/me");
        assertThat(me.statusCode()).isEqualTo(200);
        long version = version(me.body());
        String name = "Synthetic " + UUID.randomUUID().toString().substring(0, 8);

        HttpResponse<String> noCsrf = webPatchMe(browser, "{\"expectedVersion\":" + version + ",\"displayName\":\"" + name + "\"}", false);
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(webGet(browser, "/api/v1/me").body()).doesNotContain(name);

        HttpResponse<String> ok = webPatchMe(browser, "{\"expectedVersion\":" + version + ",\"displayName\":\"" + name + "\"}", true);
        assertThat(ok.statusCode()).as(ok.body()).isEqualTo(200);
        assertThat(ok.body()).contains(name);
        assertThat(version(ok.body())).isEqualTo(version + 1);

        HttpResponse<String> stale = webPatchMe(browser, "{\"expectedVersion\":" + version + ",\"locale\":\"en\"}", true);
        assertThat(stale.statusCode()).isEqualTo(409);
        assertThat(stale.body()).contains("\"code\":\"VERSION_CONFLICT\"");

        HttpResponse<String> privileged = webPatchMe(browser, "{\"expectedVersion\":" + (version + 1) + ",\"status\":\"ACTIVE\"}", true);
        assertThat(privileged.statusCode()).as("unexpected privileged field rejected").isEqualTo(400);
    }

    @Test
    void cors_allows_only_configured_web_origins() throws Exception {
        OidcBrowser browser = new OidcBrowser();
        HttpResponse<String> evil = browser.send(HttpRequest.newBuilder(URI.create(api("/api/v1/me")))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", "https://evil.invalid")
                .header("Access-Control-Request-Method", "PATCH").build());
        assertThat(evil.statusCode()).isEqualTo(403);
        assertThat(evil.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        HttpResponse<String> allowed = browser.send(HttpRequest.newBuilder(URI.create(api("/api/v1/me")))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody()).header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "PATCH").build());
        assertThat(allowed.statusCode()).isEqualTo(200);
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Origin")).hasValue("http://localhost:5173");
        assertThat(allowed.headers().firstValue("Access-Control-Allow-Credentials")).hasValue("true");
    }

    @Test
    void logout_revokes_only_the_intended_session_and_needs_csrf() throws Exception {
        OidcBrowser first = webLogin("customer-a", KeycloakTestServer.PASSWORD_A);
        OidcBrowser second = webLogin("customer-a", KeycloakTestServer.PASSWORD_A);
        assertThat(webGet(first, "/api/v1/me").statusCode()).isEqualTo(200);
        assertThat(webGet(second, "/api/v1/me").statusCode()).isEqualTo(200);
        String firstSession = new String(Base64.getDecoder().decode(first.cookie("MATBAKHY_SESSION").orElseThrow()), StandardCharsets.UTF_8);

        HttpResponse<String> forged = first.send(HttpRequest.newBuilder(URI.create(api("/api/v1/auth/logout")))
                .POST(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(forged.statusCode()).as("logout without CSRF token is refused").isEqualTo(403);
        assertThat(webGet(first, "/api/v1/me").statusCode()).isEqualTo(200);

        HttpResponse<String> logout = first.send(HttpRequest.newBuilder(URI.create(api("/api/v1/auth/logout")))
                .header("X-XSRF-TOKEN", first.cookie("XSRF-TOKEN").orElseThrow()).POST(HttpRequest.BodyPublishers.noBody()).build());
        assertThat(logout.statusCode()).isEqualTo(200);
        assertThat(logout.body()).contains(KeycloakTestServer.issuer() + "/protocol/openid-connect/logout", "id_token_hint");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE session_id = ?", Integer.class, firstSession)).isZero();

        HttpResponse<String> after = new OidcBrowser().send(HttpRequest.newBuilder(URI.create(api("/api/v1/me")))
                .header("Cookie", "MATBAKHY_SESSION=" + Base64.getEncoder().encodeToString(firstSession.getBytes(StandardCharsets.UTF_8)))
                .GET().build());
        assertThat(after.statusCode()).as("the revoked session cookie no longer authenticates").isEqualTo(401);
        assertThat(webGet(second, "/api/v1/me").statusCode()).as("other session unaffected").isEqualTo(200);
    }

    @Test
    void expired_web_session_is_denied() throws Exception {
        OidcBrowser browser = webLogin("customer-a", KeycloakTestServer.PASSWORD_A);
        assertThat(webGet(browser, "/api/v1/me").statusCode()).isEqualTo(200);
        String session = new String(Base64.getDecoder().decode(browser.cookie("MATBAKHY_SESSION").orElseThrow()), StandardCharsets.UTF_8);
        // Simulate idleness beyond the configured timeout: Spring Session judges expiry from last access + max inactive interval.
        assertThat(jdbc.update("""
                UPDATE spring_session SET last_access_time = last_access_time - (max_inactive_interval + 60) * 1000::bigint,
                       expiry_time = last_access_time - 60000 WHERE session_id = ?""", session)).isEqualTo(1);
        HttpResponse<String> expired = webGet(browser, "/api/v1/me");
        assertThat(expired.statusCode()).isEqualTo(401);
        assertThat(expired.body()).contains("\"code\":\"UNAUTHENTICATED\"");
    }
}
