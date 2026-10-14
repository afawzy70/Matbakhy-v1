package com.matbakhy.app.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Operational gauges behind the proposed alerts in docs/operations/01-environments.md (MB-039). Each is a
 * whole-system number read from the database when sampled: no per-user, per-order or other personal labels.
 * The metrics endpoint is not exposed over HTTP by default; a deployment publishes it only on a restricted
 * management port (O07).
 */
@Configuration(proxyBeanMethods = false)
class OperationalMetrics {

    @Bean
    MetricsBinder operationalGauges(MeterRegistry registry, JdbcTemplate jdbc) {
        BoundedReader reader = new BoundedReader(jdbc);
        gauge(registry, reader, "matbakhy.outbox.oldest.pending.age", "seconds",
                "Age of the oldest pending outbox event (alert: > 5 minutes)",
                "SELECT coalesce(extract(epoch FROM now() - min(created_at)), 0) FROM outbox_event WHERE status = 'PENDING'");
        gauge(registry, reader, "matbakhy.outbox.failed", "events",
                "Outbox events that exhausted their retries (alert: > 0)",
                "SELECT count(*) FROM outbox_event WHERE status = 'FAILED'");
        gauge(registry, reader, "matbakhy.notifications.dead", "deliveries",
                "Channel deliveries in the dead letter (alert: growing)",
                "SELECT count(*) FROM notification_delivery WHERE status = 'DEAD'");
        gauge(registry, reader, "matbakhy.notifications.oldest.pending.age", "seconds",
                "Age of the oldest due channel delivery (provider outage indicator)",
                "SELECT coalesce(extract(epoch FROM now() - min(next_attempt_at)), 0) FROM notification_delivery "
                        + "WHERE status = 'PENDING' AND next_attempt_at <= now()");
        gauge(registry, reader, "matbakhy.ledger.unbalanced.groups", "groups",
                "Ledger groups whose debits differ from credits (critical: > 0)",
                "SELECT count(*) FROM ledger_group g WHERE (SELECT coalesce(sum(CASE direction WHEN 'DEBIT' THEN amount_minor ELSE -amount_minor END), 0) "
                        + "FROM ledger_entry e WHERE e.group_id = g.id) <> 0 OR NOT EXISTS (SELECT 1 FROM ledger_entry e WHERE e.group_id = g.id)");
        gauge(registry, reader, "matbakhy.orders.acceptance.overdue", "orders",
                "Orders still pending acceptance after their deadline (expiry job lagging)",
                "SELECT count(*) FROM customer_order WHERE state = 'PENDING_ACCEPTANCE' AND accept_by < now() - interval '2 minutes'");
        return new MetricsBinder();
    }

    private static void gauge(MeterRegistry registry, BoundedReader reader, String name, String unit, String description, String sql) {
        // Strong reference: the reader is not a bean, and a gauge otherwise holds its state weakly (it would read NaN after GC).
        Gauge.builder(name, reader, r -> r.read(sql)).strongReference(true).baseUnit(unit).description(description).register(registry);
    }

    /**
     * Reads a gauge with a time budget so that a scrape never waits for the database (MB-039). A failed or slow read
     * reports NaN rather than a misleading zero, and after a slow read the other gauges report NaN at once for a short
     * back-off, so a scrape during an outage still completes well inside Prometheus' timeout and the DatabaseUnavailable
     * alert can fire. The reads run on two daemon threads with a small queue; a full queue also reports NaN.
     */
    static final class BoundedReader {

        private static final long BUDGET_MILLIS = 1500;
        private static final long BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(10);

        private final JdbcTemplate jdbc;
        private final ThreadPoolExecutor pool;
        private volatile long unavailableUntil = System.nanoTime();

        BoundedReader(JdbcTemplate jdbc) {
            this.jdbc = jdbc;
            this.pool = new ThreadPoolExecutor(2, 2, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(12), r -> {
                Thread t = new Thread(r, "matbakhy-gauges");
                t.setDaemon(true);
                return t;
            }, new ThreadPoolExecutor.AbortPolicy());
        }

        double read(String sql) {
            if (System.nanoTime() - unavailableUntil < 0) {
                return Double.NaN;
            }
            Future<Number> value;
            try {
                value = pool.submit(() -> jdbc.queryForObject(sql, Number.class));
            } catch (RuntimeException rejected) {
                return Double.NaN;
            }
            try {
                Number n = value.get(BUDGET_MILLIS, TimeUnit.MILLISECONDS);
                return n == null ? Double.NaN : n.doubleValue();
            } catch (TimeoutException slow) {
                value.cancel(true);
                unavailableUntil = System.nanoTime() + BACKOFF_NANOS;
                return Double.NaN;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return Double.NaN;
            } catch (Exception failed) {
                return Double.NaN;
            }
        }
    }

    /** Marker so the gauges are registered once with the context. */
    static final class MetricsBinder {
    }
}
