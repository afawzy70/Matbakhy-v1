package com.matbakhy.app.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.matbakhy.app.support.IntegrationTest;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;

/** MB-039: operational gauges read real state, carry no personal labels and are not published over HTTP by default. */
@IntegrationTest
class OperationalMetricsIntegrationTest {

    @Autowired MeterRegistry registry;
    @Autowired JdbcTemplate jdbc;
    @LocalServerPort int port;

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }

    @Test
    void outbox_gauges_follow_failed_and_stale_events() {
        double failedBefore = gauge("matbakhy.outbox.failed");
        UUID failed = UUID.randomUUID();
        UUID stale = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, status, available_at, created_at)
                VALUES (?, 'order', ?, 'ORDER_PLACED', '{}', 'FAILED', now(), now())""", failed, UUID.randomUUID());
        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, status, available_at, created_at)
                VALUES (?, 'order', ?, 'ORDER_PLACED', '{}', 'PENDING', now() + interval '1 day', now() - interval '10 minutes')""", stale, UUID.randomUUID());
        try {
            assertThat(gauge("matbakhy.outbox.failed")).isCloseTo(failedBefore + 1, within(0.001));
            assertThat(gauge("matbakhy.outbox.oldest.pending.age")).as("10-minute-old pending event breaches the 5-minute alert").isGreaterThanOrEqualTo(600);
        } finally {
            jdbc.update("DELETE FROM outbox_event WHERE id IN (?, ?)", failed, stale);
        }
    }

    @Test
    void every_gauge_is_registered_reads_a_number_and_has_no_tags() {
        for (String name : new String[] {"matbakhy.outbox.oldest.pending.age", "matbakhy.outbox.failed", "matbakhy.notifications.dead",
                "matbakhy.notifications.oldest.pending.age", "matbakhy.ledger.unbalanced.groups", "matbakhy.orders.acceptance.overdue"}) {
            Meter meter = registry.get(name).meter();
            assertThat(meter.getId().getTags()).as(name + " has no labels").isEmpty();
            assertThat(gauge(name)).as(name).isNotNaN().isGreaterThanOrEqualTo(0);
        }
        assertThat(gauge("matbakhy.ledger.unbalanced.groups")).as("the database enforces balance, so this stays 0").isZero();
    }

    @Test
    void metrics_are_not_published_over_http_by_default() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        HttpResponse<String> metrics = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/metrics")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(metrics.statusCode()).isIn(401, 403, 404);
        assertThat(metrics.body()).doesNotContain("matbakhy.outbox");
        HttpResponse<String> health = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/actuator/health")).build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(health.statusCode()).isEqualTo(200);
    }
}
