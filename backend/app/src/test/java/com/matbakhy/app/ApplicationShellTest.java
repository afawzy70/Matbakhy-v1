package com.matbakhy.app;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;

@IntegrationTest
class ApplicationShellTest {

    @LocalServerPort
    int port;

    private HttpResponse<String> get(String path) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void application_starts_and_reports_health_without_details() throws Exception {
        HttpResponse<String> response = get("/actuator/health");
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"status\":\"UP\"").doesNotContain("components", "details", "diskSpace");
    }

    @Test
    void non_health_actuator_endpoints_are_not_exposed() throws Exception {
        // Not exposed (management exposure is health only) and, since MB-009, also denied before routing.
        HttpResponse<String> env = get("/actuator/env");
        assertThat(env.statusCode()).isIn(401, 403, 404);
        assertThat(env.body()).doesNotContain("propertySources", "activeProfiles", "systemProperties");
    }
}
