package com.matbakhy.app.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.IntegrationTest;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;

/**
 * MB-039 (OPS-02, local): a deployment that sets a separate management port publishes health and Prometheus metrics
 * there only. The API port, which the proxy forwards, publishes neither. The scrape carries whole-system gauges without
 * personal labels.
 */
@IntegrationTest
@TestPropertySource(properties = {"management.server.port=0", "management.endpoints.web.exposure.include=health,prometheus"})
class ManagementPortIntegrationTest {

    @LocalServerPort int apiPort;
    @LocalManagementPort int managementPort;

    private static HttpResponse<String> get(int port, String path) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void prometheus_is_served_on_the_management_port_only() throws Exception {
        HttpResponse<String> scrape = get(managementPort, "/actuator/prometheus");
        assertThat(scrape.statusCode()).isEqualTo(200);
        assertThat(scrape.body()).contains("matbakhy_outbox_failed_events", "matbakhy_ledger_unbalanced_groups ",
                "matbakhy_audit_write_failures_events_total");
        assertThat(scrape.body()).as("no personal or object labels").doesNotContain("phone", "email", "actor", "order_id", "user_id");
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);
        assertThat(get(managementPort, "/actuator/env").statusCode()).isIn(401, 403, 404);

        HttpResponse<String> onApi = get(apiPort, "/actuator/prometheus");
        assertThat(onApi.statusCode()).isIn(401, 403, 404);
        assertThat(onApi.body()).doesNotContain("matbakhy_outbox");
    }
}
