package com.matbakhy.app.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.IntegrationTest;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * MB-039 (OPS-02), found in the local observability rehearsal: while the database is unreachable, a scrape of the
 * operational gauges must return quickly with NaN (so the DatabaseUnavailable alert fires) instead of waiting for a
 * connection per gauge, which made the scrape time out and raised ApiDown instead. Uses the deployed connection timeout
 * (5 s). Own context and database container (the property below), so pausing it affects no other test.
 */
@IntegrationTest
@TestPropertySource(properties = "matbakhy.test.context=metrics-outage")
class OperationalMetricsOutageIntegrationTest {

    private static final String[] GAUGES = {"matbakhy.outbox.oldest.pending.age", "matbakhy.outbox.failed", "matbakhy.notifications.dead",
        "matbakhy.notifications.oldest.pending.age", "matbakhy.ledger.unbalanced.groups", "matbakhy.orders.acceptance.overdue"};

    @Autowired MeterRegistry registry;
    @Autowired PostgreSQLContainer postgres;

    private Duration scrape(double[] into) {
        long started = System.nanoTime();
        for (int i = 0; i < GAUGES.length; i++) {
            into[i] = registry.get(GAUGES[i]).gauge().value();
        }
        return Duration.ofNanos(System.nanoTime() - started);
    }

    @Test
    void a_scrape_during_a_database_outage_is_fast_and_reports_nan_then_recovers() throws Exception {
        double[] values = new double[GAUGES.length];
        scrape(values);
        assertThat(java.util.Arrays.stream(values).boxed().toList()).allSatisfy(v -> assertThat(v).isNotNaN());

        postgres.getDockerClient().pauseContainerCmd(postgres.getContainerId()).exec();
        try {
            Duration first = scrape(values);
            assertThat(first).as("whole scrape well inside Prometheus' 10 s timeout").isLessThan(Duration.ofSeconds(4));
            assertThat(java.util.Arrays.stream(values).boxed().toList()).allSatisfy(v -> assertThat(v).isNaN());
            Duration second = scrape(values);
            assertThat(second).as("repeated scrape while still down").isLessThan(Duration.ofSeconds(4));
            assertThat(java.util.Arrays.stream(values).boxed().toList()).allSatisfy(v -> assertThat(v).isNaN());
        } finally {
            postgres.getDockerClient().unpauseContainerCmd(postgres.getContainerId()).exec();
        }

        boolean recovered = false;
        for (int i = 0; i < 40 && !recovered; i++) {
            scrape(values);
            recovered = java.util.Arrays.stream(values).noneMatch(Double::isNaN);
            if (!recovered) {
                Thread.sleep(1000);
            }
        }
        assertThat(recovered).as("gauges read real values again without a restart").isTrue();
    }
}
