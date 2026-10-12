package com.matbakhy.app.observability;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
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
        gauge(registry, jdbc, "matbakhy.outbox.oldest.pending.age", "seconds",
                "Age of the oldest pending outbox event (alert: > 5 minutes)",
                "SELECT coalesce(extract(epoch FROM now() - min(created_at)), 0) FROM outbox_event WHERE status = 'PENDING'");
        gauge(registry, jdbc, "matbakhy.outbox.failed", "events",
                "Outbox events that exhausted their retries (alert: > 0)",
                "SELECT count(*) FROM outbox_event WHERE status = 'FAILED'");
        gauge(registry, jdbc, "matbakhy.notifications.dead", "deliveries",
                "Channel deliveries in the dead letter (alert: growing)",
                "SELECT count(*) FROM notification_delivery WHERE status = 'DEAD'");
        gauge(registry, jdbc, "matbakhy.notifications.oldest.pending.age", "seconds",
                "Age of the oldest due channel delivery (provider outage indicator)",
                "SELECT coalesce(extract(epoch FROM now() - min(next_attempt_at)), 0) FROM notification_delivery "
                        + "WHERE status = 'PENDING' AND next_attempt_at <= now()");
        gauge(registry, jdbc, "matbakhy.ledger.unbalanced.groups", "groups",
                "Ledger groups whose debits differ from credits (critical: > 0)",
                "SELECT count(*) FROM ledger_group g WHERE (SELECT coalesce(sum(CASE direction WHEN 'DEBIT' THEN amount_minor ELSE -amount_minor END), 0) "
                        + "FROM ledger_entry e WHERE e.group_id = g.id) <> 0 OR NOT EXISTS (SELECT 1 FROM ledger_entry e WHERE e.group_id = g.id)");
        gauge(registry, jdbc, "matbakhy.orders.acceptance.overdue", "orders",
                "Orders still pending acceptance after their deadline (expiry job lagging)",
                "SELECT count(*) FROM customer_order WHERE state = 'PENDING_ACCEPTANCE' AND accept_by < now() - interval '2 minutes'");
        return new MetricsBinder();
    }

    private static void gauge(MeterRegistry registry, JdbcTemplate jdbc, String name, String unit, String description, String sql) {
        Gauge.builder(name, jdbc, j -> read(j, sql)).baseUnit(unit).description(description).register(registry);
    }

    /** A failed read (for example, the database is down) reports NaN rather than a misleading zero. */
    private static double read(JdbcTemplate jdbc, String sql) {
        try {
            Number value = jdbc.queryForObject(sql, Number.class);
            return value == null ? Double.NaN : value.doubleValue();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    /** Marker so the gauges are registered once with the context. */
    static final class MetricsBinder {
    }
}
