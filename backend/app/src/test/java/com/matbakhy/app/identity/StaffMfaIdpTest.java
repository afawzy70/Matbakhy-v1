package com.matbakhy.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.KeycloakTestServer;
import com.matbakhy.app.support.OidcBrowser;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

/**
 * CR-002 / BR-007 against real Keycloak (the realm files under infra/ and deploy/ carry the same flow): the identity provider
 * itself refuses to issue an authorization code to a staff account (realm role matbakhy-staff) without a valid OTP, forces
 * enrolment when none is configured, issues an amr containing otp on success, and leaves customers on password-only login.
 * Synthetic accounts; the password and TOTP secret are generated per run.
 */
class StaffMfaIdpTest {

    private static final String CLIENT = "matbakhy-mobile";
    private static final String REDIRECT = "matbakhy-dev://auth/callback";
    private static final Pattern ANY_FORM_ACTION = Pattern.compile("<form[^>]*action=\"([^\"]+)\"");
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    private record Outcome(String location, String body, OidcBrowser browser, String verifier) {
        boolean issuedCode() {
            return location != null && location.startsWith(REDIRECT) && OidcBrowser.queryParam(location, "code") != null;
        }
    }

    /** RFC 6238 with the secret string's bytes as the key, as Keycloak stores it. */
    static String totp(String secret, long epochSeconds) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(epochSeconds / 30).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        return String.format("%06d", binary % 1_000_000);
    }

    private static String formAction(String body) {
        Matcher m = ANY_FORM_ACTION.matcher(body);
        assertThat(m.find()).as("a form is expected").isTrue();
        return m.group(1).replace("&amp;", "&");
    }

    private HttpResponse<String> post(OidcBrowser browser, String url, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
    }

    /** Password step, then (when the provider asks) the OTP step with the given code supplier. */
    private Outcome login(String user, String password, String otpOrNull) throws Exception {
        OidcBrowser browser = new OidcBrowser();
        String verifier = OidcBrowser.randomVerifier();
        String authorize = KeycloakTestServer.issuer() + "/protocol/openid-connect/auth?response_type=code&scope=openid&client_id=" + CLIENT
                + "&redirect_uri=" + OidcBrowser.enc(REDIRECT) + "&state=" + UUID.randomUUID()
                + "&code_challenge=" + OidcBrowser.challenge(verifier) + "&code_challenge_method=S256";
        HttpResponse<String> page = browser.get(authorize);
        HttpResponse<String> next = post(browser, formAction(page.body()), "username=" + OidcBrowser.enc(user) + "&password=" + OidcBrowser.enc(password) + "&credentialId=");
        String location = next.headers().firstValue("Location").orElse(null);
        if (location != null && !location.startsWith(REDIRECT)) {
            // e.g. required-action CONFIGURE_TOTP: follow it to see what the provider demands
            next = browser.get(URI.create(authorize).resolve(location).toString());
            location = next.headers().firstValue("Location").orElse(null);
        }
        String body = next.body();
        if (location == null && body.contains("name=\"otp\"") && otpOrNull != null) {
            next = post(browser, formAction(body), "otp=" + OidcBrowser.enc(otpOrNull) + "&login=Sign+In");
            location = next.headers().firstValue("Location").orElse(null);
            body = next.body();
        }
        return new Outcome(location, body, browser, verifier);
    }

    private String accessToken(Outcome o) throws Exception {
        String form = "grant_type=authorization_code&client_id=" + CLIENT + "&redirect_uri=" + OidcBrowser.enc(REDIRECT)
                + "&code=" + OidcBrowser.enc(OidcBrowser.queryParam(o.location(), "code")) + "&code_verifier=" + o.verifier();
        HttpResponse<String> token = post(o.browser(), KeycloakTestServer.issuer() + "/protocol/openid-connect/token", form);
        assertThat(token.statusCode()).as(token.body()).isEqualTo(200);
        Matcher m = ACCESS_TOKEN.matcher(token.body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    private static String claims(String jwt) {
        return new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }

    @Test
    void a_staff_account_with_only_a_password_never_receives_an_authorization_code() throws Exception {
        Outcome o = login("staff-mfa", KeycloakTestServer.STAFF_PASSWORD, null);
        assertThat(o.issuedCode()).as("password alone must not complete a staff login").isFalse();
        assertThat(o.body()).contains("name=\"otp\"");
    }

    @Test
    void a_wrong_one_time_code_is_refused() throws Exception {
        String valid = totp(KeycloakTestServer.STAFF_TOTP_SECRET, System.currentTimeMillis() / 1000);
        String wrong = String.format("%06d", (Integer.parseInt(valid) + 1) % 1_000_000);
        assertThat(login("staff-mfa", KeycloakTestServer.STAFF_PASSWORD, wrong).issuedCode()).isFalse();
    }

    @Test
    void a_staff_account_without_an_enrolled_authenticator_is_forced_to_enrol_not_admitted() throws Exception {
        Outcome o = login("staff-no-otp", KeycloakTestServer.STAFF_PASSWORD, null);
        assertThat(o.issuedCode()).as("no OTP configured must not mean no OTP required").isFalse();
        assertThat(o.body().toLowerCase()).containsAnyOf("totp", "authenticator");
    }

    @Test
    void a_valid_one_time_code_completes_login_and_the_token_asserts_otp() throws Exception {
        Outcome o = login("staff-mfa", KeycloakTestServer.STAFF_PASSWORD, totp(KeycloakTestServer.STAFF_TOTP_SECRET, System.currentTimeMillis() / 1000));
        assertThat(o.issuedCode()).isTrue();
        String claims = claims(accessToken(o));
        assertThat(claims).containsPattern("\"amr\":\\[[^\\]]*\"otp\"").contains("\"auth_time\"");
    }

    @Test
    void customers_keep_password_only_login() throws Exception {
        Outcome o = login("customer-a", KeycloakTestServer.PASSWORD_A, null);
        assertThat(o.issuedCode()).isTrue();
        assertThat(claims(accessToken(o))).doesNotContain("\"otp\"");
    }
}
