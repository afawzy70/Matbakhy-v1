package com.matbakhy.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.matbakhy.app.support.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

/** Without WEB_COOKIE_DOMAIN (local development, where the API and the web apps share localhost) the CSRF cookie stays host-only. */
@IntegrationTest
@AutoConfigureMockMvc
class CsrfCookieDefaultIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    void the_csrf_cookie_has_no_domain_by_default_and_is_readable_by_scripts() throws Exception {
        List<String> cookies = mvc.perform(get("/api/v1/configuration/public")).andReturn().getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        String xsrf = cookies.stream().filter(c -> c.startsWith("XSRF-TOKEN=")).findFirst().orElseThrow();
        assertThat(xsrf).doesNotContain("Domain=").doesNotContain("HttpOnly");
    }
}
