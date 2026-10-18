package com.matbakhy.app.support;

import static org.assertj.core.api.Assertions.assertThat;

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

/**
 * Drives the real Keycloak browser flow (password, optional OTP, TOTP enrolment) with the test realm's synthetic accounts.
 * A native PKCE client is used so no client secret is involved.
 */
public final class MfaBrowser {

    public static final String CLIENT = "matbakhy-mobile";
    public static final String REDIRECT = "matbakhy-dev://auth/callback";
    private static final Pattern ANY_FORM_ACTION = Pattern.compile("<form[^>]*action=\"([^\"]+)\"");
    private static final Pattern TOTP_SECRET = Pattern.compile("name=\"totpSecret\"\\s+value=\"([^\"]+)\"");
    private static final Pattern ACCESS_TOKEN = Pattern.compile("\"access_token\"\\s*:\\s*\"([^\"]+)\"");

    /** The state after the browser stopped: either a redirect carrying a code, or a page the provider wants answered. */
    public record Outcome(String location, String body, OidcBrowser browser, String verifier) {
        public boolean issuedCode() {
            return location != null && location.startsWith(REDIRECT) && OidcBrowser.queryParam(location, "code") != null;
        }

        public boolean asksForOtp() {
            return body != null && body.contains("name=\"otp\"");
        }

        public boolean asksForEnrolment() {
            return body != null && body.contains("name=\"totpSecret\"");
        }
    }

    private MfaBrowser() {
    }

    /** RFC 6238 with the secret string's bytes as the key, as Keycloak stores it. */
    public static String totp(String secret, long epochSeconds) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA1");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA1"));
        byte[] hash = mac.doFinal(ByteBuffer.allocate(8).putLong(epochSeconds / 30).array());
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24) | ((hash[offset + 1] & 0xff) << 16) | ((hash[offset + 2] & 0xff) << 8) | (hash[offset + 3] & 0xff);
        return String.format("%06d", binary % 1_000_000);
    }

    public static String now(String secret) throws Exception {
        return totp(secret, System.currentTimeMillis() / 1000);
    }

    public static String authorizeUrl(String verifier, boolean stepUp) {
        return KeycloakTestServer.issuer() + "/protocol/openid-connect/auth?response_type=code&scope=openid&client_id=" + CLIENT
                + "&redirect_uri=" + OidcBrowser.enc(REDIRECT) + "&state=" + UUID.randomUUID()
                + "&code_challenge=" + OidcBrowser.challenge(verifier) + "&code_challenge_method=S256"
                + (stepUp ? "&acr_values=" + OidcBrowser.enc("mfa 2") : "");
    }

    private static String formAction(String body) {
        Matcher m = ANY_FORM_ACTION.matcher(body);
        assertThat(m.find()).as("a form is expected").isTrue();
        return m.group(1).replace("&amp;", "&");
    }

    private static HttpResponse<String> post(OidcBrowser browser, String url, String form) throws Exception {
        return browser.send(HttpRequest.newBuilder(URI.create(url)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build());
    }

    /**
     * Password step, then the OTP step when the provider asks and {@code otpOrNull} is given. A required-action page
     * (e.g. TOTP enrolment) is followed so the caller can see what the provider demands.
     */
    public static Outcome login(OidcBrowser browser, String user, String password, String otpOrNull, boolean stepUp) throws Exception {
        String verifier = OidcBrowser.randomVerifier();
        HttpResponse<String> page = browser.get(authorizeUrl(verifier, stepUp));
        if (page.headers().firstValue("Location").isPresent() && !page.body().contains("kc-form-login")) {
            // Already signed in (SSO cookie) and nothing more is demanded.
            return new Outcome(page.headers().firstValue("Location").get(), page.body(), browser, verifier);
        }
        if (!page.body().contains("kc-form-login")) {
            return new Outcome(null, page.body(), browser, verifier); // e.g. the OTP page for an existing SSO session
        }
        HttpResponse<String> next = post(browser, formAction(page.body()), "username=" + OidcBrowser.enc(user) + "&password="
                + OidcBrowser.enc(password) + "&credentialId=");
        return afterPassword(browser, next, otpOrNull, verifier, stepUp);
    }

    public static Outcome login(String user, String password, String otpOrNull, boolean stepUp) throws Exception {
        return login(new OidcBrowser(), user, password, otpOrNull, stepUp);
    }

    private static Outcome afterPassword(OidcBrowser browser, HttpResponse<String> next, String otpOrNull, String verifier, boolean stepUp) throws Exception {
        String location = next.headers().firstValue("Location").orElse(null);
        if (location != null && !location.startsWith(REDIRECT)) {
            next = browser.get(URI.create(authorizeUrl(verifier, stepUp)).resolve(location).toString());
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

    public record Enrolment(String secret, Outcome finished) {
    }

    /** Completes the CONFIGURE_TOTP page shown to an account without a factor; the login then finishes with a code. */
    public static Enrolment enrol(Outcome pending, String label) throws Exception {
        assertThat(pending.asksForEnrolment()).as("the provider must be asking for enrolment").isTrue();
        Matcher secret = TOTP_SECRET.matcher(pending.body());
        assertThat(secret.find()).isTrue();
        String raw = secret.group(1);
        HttpResponse<String> done = post(pending.browser(), formAction(pending.body()), "totp=" + OidcBrowser.enc(now(raw))
                + "&totpSecret=" + OidcBrowser.enc(raw) + "&userLabel=" + OidcBrowser.enc(label) + "&submitAction=Save");
        return new Enrolment(raw, new Outcome(done.headers().firstValue("Location").orElse(null), done.body(), pending.browser(), pending.verifier()));
    }

    /** Answers an OTP page that is already showing (e.g. a step-up on an existing SSO session). */
    public static Outcome submitOtp(Outcome pending, String otp) throws Exception {
        assertThat(pending.asksForOtp()).isTrue();
        HttpResponse<String> next = post(pending.browser(), formAction(pending.body()), "otp=" + OidcBrowser.enc(otp) + "&login=Sign+In");
        return new Outcome(next.headers().firstValue("Location").orElse(null), next.body(), pending.browser(), pending.verifier());
    }

    /** Exchanges the code of a finished login for an access token. */
    public static String accessToken(Outcome o) throws Exception {
        return accessToken(o.browser(), o.location(), o.verifier());
    }

    public record Tokens(String access, String refresh) {
    }

    /** Access and refresh token of a finished login. */
    public static Tokens tokens(Outcome o) throws Exception {
        String form = "grant_type=authorization_code&client_id=" + CLIENT + "&redirect_uri=" + OidcBrowser.enc(REDIRECT)
                + "&code=" + OidcBrowser.enc(OidcBrowser.queryParam(o.location(), "code")) + "&code_verifier=" + o.verifier();
        HttpResponse<String> token = post(o.browser(), KeycloakTestServer.issuer() + "/protocol/openid-connect/token", form);
        assertThat(token.statusCode()).as(token.body()).isEqualTo(200);
        Matcher a = ACCESS_TOKEN.matcher(token.body());
        Matcher r = Pattern.compile("\"refresh_token\"\\s*:\\s*\"([^\"]+)\"").matcher(token.body());
        assertThat(a.find() && r.find()).isTrue();
        return new Tokens(a.group(1), r.group(1));
    }

    /** HTTP status of a refresh-token exchange: 200 while the provider session lives, 400 once it was ended. */
    public static int refreshStatus(String refreshToken) throws Exception {
        return post(new OidcBrowser(), KeycloakTestServer.issuer() + "/protocol/openid-connect/token",
                "grant_type=refresh_token&client_id=" + CLIENT + "&refresh_token=" + OidcBrowser.enc(refreshToken)).statusCode();
    }

    public static String accessToken(OidcBrowser browser, String location, String verifier) throws Exception {
        String form = "grant_type=authorization_code&client_id=" + CLIENT + "&redirect_uri=" + OidcBrowser.enc(REDIRECT)
                + "&code=" + OidcBrowser.enc(OidcBrowser.queryParam(location, "code")) + "&code_verifier=" + verifier;
        HttpResponse<String> token = post(browser, KeycloakTestServer.issuer() + "/protocol/openid-connect/token", form);
        assertThat(token.statusCode()).as(token.body()).isEqualTo(200);
        Matcher m = ACCESS_TOKEN.matcher(token.body());
        assertThat(m.find()).isTrue();
        return m.group(1);
    }

    public static String claims(String jwt) {
        return new String(java.util.Base64.getUrlDecoder().decode(jwt.split("\\.")[1]), StandardCharsets.UTF_8);
    }
}
