package com.matbakhy.app.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MB-037 / TC-137: a real PostgreSQL outage (the container is paused). While the database is unreachable, requests
 * fail within the configured socket timeout (3 s here; 30 s by default) with a retryable 503 DEPENDENCY_UNAVAILABLE
 * that reveals no database details. Without that timeout the first run of this test hung. After the database returns,
 * the same running application serves reads and writes again without a restart. This class has its own application
 * context and database container, so pausing it affects no other test.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
@TestPropertySource(properties = {"spring.datasource.hikari.connection-timeout=1500", "spring.datasource.hikari.validation-timeout=1000",
    "spring.datasource.hikari.data-source-properties.socketTimeout=3"})
class DatabaseOutageRecoveryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired PostgreSQLContainer postgres;

    private MockHttpServletResponse zones() throws Exception {
        return mvc.perform(get("/api/v1/zones")).andReturn().getResponse();
    }

    @Test
    void requests_fail_fast_as_retryable_503_during_an_outage_and_recover_without_restart() throws Exception {
        AccountAuthentication customer = logins.login(UUID.randomUUID().toString(), "Outage Customer");
        assertThat(zones().getStatus()).isEqualTo(200);
        assertThat(mvc.perform(get("/api/v1/me").with(authentication(customer))).andReturn().getResponse().getStatus()).isEqualTo(200);

        postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
        try {
            long started = System.nanoTime();
            MockHttpServletResponse down = zones();
            Duration took = Duration.ofNanos(System.nanoTime() - started);
            assertThat(down.getStatus()).as(down.getContentAsString()).isEqualTo(503);
            JsonNode error = json.readTree(down.getContentAsString());
            assertThat(error.get("code").asString()).isEqualTo("DEPENDENCY_UNAVAILABLE");
            assertThat(error.get("retryable").asBoolean()).isTrue();
            assertThat(down.getContentAsString().toLowerCase()).doesNotContain("jdbc").doesNotContain("postgres").doesNotContain("hikari")
                    .doesNotContain("sql").doesNotContain("exception");
            assertThat(took).as("fails fast instead of hanging").isLessThan(Duration.ofSeconds(10));
        } finally {
            postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
        }

        // Same application instance, no restart: reads and writes work again once the database is back.
        int status = 0;
        for (int i = 0; i < 30 && status != 200; i++) {
            status = zones().getStatus();
            if (status != 200) {
                Thread.sleep(1000);
            }
        }
        assertThat(status).as("recovered without restart").isEqualTo(200);
        assertThat(mvc.perform(get("/api/v1/me").with(authentication(customer))).andReturn().getResponse().getStatus()).isEqualTo(200);
        long version = json.readTree(mvc.perform(get("/api/v1/me").with(authentication(customer))).andReturn().getResponse().getContentAsString())
                .get("version").asLong();
        MockHttpServletResponse write = mvc.perform(patch("/api/v1/me").with(csrf()).with(authentication(customer)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"expectedVersion\":" + version + ",\"displayName\":\"Back Online\"}")).andReturn().getResponse();
        assertThat(write.getStatus()).as(write.getContentAsString()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT display_name FROM app_user WHERE id = ?", String.class, customer.getPrincipal().id()))
                .isEqualTo("Back Online");
    }
}
