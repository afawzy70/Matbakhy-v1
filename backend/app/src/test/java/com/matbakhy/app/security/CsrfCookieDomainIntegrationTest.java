package com.matbakhy.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.matbakhy.app.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Found by the remote browser E2E on a deployed topology: the web apps read the XSRF-TOKEN cookie with document.cookie, but the API
 * (api.<domain>) sets it for its own host only, so the apps on app.<domain> and ops.<domain> never saw it and every browser POST was
 * refused with 403. Dev hid it because everything shares localhost. A deployment sets WEB_COOKIE_DOMAIN to the parent domain, so the
 * cookie is readable by all of its web apps (and still sent back to the API).
 */
@IntegrationTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "matbakhy.identity.web-cookie-domain=example.test")
class CsrfCookieDomainIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void the_csrf_cookie_is_set_for_the_parent_domain_when_the_deployment_names_one() throws Exception {
        List<String> cookies = mvc.perform(get("/api/v1/configuration/public")).andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String xsrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(xsrf).contains("Domain=example.test").contains("Path=/").doesNotContain("HttpOnly");
    }
}
