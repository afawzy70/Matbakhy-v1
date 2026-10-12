package com.matbakhy.app.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matbakhy.app.config.MatbakhyProperties;
import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import com.matbakhy.app.config.MatbakhyProperties.Market;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.session.web.http.CookieSerializer.CookieValue;

/** A PREVIEW is served over HTTPS like any deployed environment: secure session cookie and a required web client secret. */
class PreviewSecurityConfigurationTest {

    private static final MatbakhyProperties PREVIEW = new MatbakhyProperties(DeploymentEnvironment.PREVIEW, new Market("EGP", "Africa/Cairo"));

    @Test
    void the_session_cookie_is_secure() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        new SecurityConfiguration().sessionCookie(PREVIEW).writeCookieValue(new CookieValue(new MockHttpServletRequest(), response, "s"));
        org.assertj.core.api.Assertions.assertThat(response.getHeader("Set-Cookie")).contains("Secure");
    }

    @Test
    void the_web_client_secret_is_required() {
        IdentityProperties identity = new IdentityProperties("https://id.example.test/realms/matbakhy", null, "matbakhy-api",
                "matbakhy-web", "", java.util.List.of("https://app.example.test"), "https://app.example.test/",
                java.util.List.of("mfa"), java.time.Duration.ofMinutes(10), null);
        assertThatThrownBy(() -> new SecurityConfiguration().clientRegistrations(identity, PREVIEW)).hasMessageContaining("OIDC_WEB_CLIENT_SECRET");
    }
}
