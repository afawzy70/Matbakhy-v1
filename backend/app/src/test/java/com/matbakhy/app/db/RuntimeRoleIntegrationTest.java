package com.matbakhy.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matbakhy.app.support.IntegrationTest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * MB-039 / AUD-01 (local): the API's least-privilege runtime role, granted by the Flyway afterMigrate callback run as the
 * migration owner. Connected as that role, data access works, but audit and other append-only history cannot be updated,
 * deleted or truncated, and the protecting triggers cannot be disabled or dropped; no DDL is possible. Support messages
 * and payment events allow only the account-erasure change their triggers permit (V27).
 */
@IntegrationTest
class RuntimeRoleIntegrationTest {

    private static final String ROLE = "matbakhy_runtime_test";
    private static final String PASSWORD = "runtime-" + UUID.randomUUID();

    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;

    @Test
    void the_runtime_role_reads_and_writes_data_but_cannot_rewrite_history_or_change_the_schema() throws Exception {
        jdbc.execute("DO $$ BEGIN IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = '" + ROLE + "') THEN CREATE ROLE " + ROLE
                + " LOGIN; END IF; END $$");
        jdbc.execute("ALTER ROLE " + ROLE + " LOGIN PASSWORD '" + PASSWORD + "' NOSUPERUSER NOCREATEDB NOCREATEROLE");
        // The real callback path: Flyway runs afterMigrate.sql (as the owner) even when no migration is pending.
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").placeholders(Map.of("runtimeRole", ROLE)).load().migrate();

        String url;
        try (Connection owner = dataSource.getConnection()) {
            url = owner.getMetaData().getURL();
        }
        try (Connection c = DriverManager.getConnection(url, ROLE, PASSWORD); Statement s = c.createStatement()) {
            UUID user = UUID.randomUUID();
            s.executeUpdate("INSERT INTO app_user (id, issuer, subject, display_name) VALUES ('" + user + "', 'runtime-test', '" + user + "', 'Runtime')");
            assertThat(s.executeUpdate("UPDATE app_user SET display_name = 'Runtime 2' WHERE id = '" + user + "'")).isEqualTo(1);
            s.executeUpdate("INSERT INTO audit_event (id, actor_id, action, object_type, object_id, outcome, reason, details) VALUES ('"
                    + UUID.randomUUID() + "', '" + user + "', 'runtime.check', 'test', 'x', 'ALLOWED', 'runtime role test', '{}'::jsonb)");

            for (String forbidden : List.of(
                    "UPDATE audit_event SET reason = 'tampered'",
                    "DELETE FROM audit_event",
                    "TRUNCATE audit_event",
                    "ALTER TABLE audit_event DISABLE TRIGGER audit_event_no_update",
                    "DROP TRIGGER audit_event_no_update ON audit_event",
                    "SET session_replication_role = replica",
                    "UPDATE ledger_entry SET amount_minor = 0",
                    "DELETE FROM order_item",
                    "UPDATE meal_revision SET price_minor = 1",
                    "DELETE FROM customer_order",
                    "UPDATE flyway_schema_history SET success = false",
                    "DELETE FROM case_message",
                    "TRUNCATE case_message",
                    "ALTER TABLE case_message DISABLE TRIGGER case_message_append_only",
                    "DELETE FROM payment_event",
                    "TRUNCATE payment_event",
                    "ALTER TABLE payment_event DISABLE TRIGGER payment_event_append_only",
                    "CREATE TABLE runtime_probe (id int)")) {
                assertThatThrownBy(() -> s.execute(forbidden)).as(forbidden).isInstanceOf(SQLException.class)
                        .satisfies(e -> assertThat(((SQLException) e).getSQLState()).as(forbidden).isEqualTo("42501"));
            }
        }

        // Every application table is readable and writable by the runtime role (it can run the API), the history is not.
        List<String> missing = jdbc.queryForList("""
                SELECT tablename FROM pg_tables WHERE schemaname = 'public' AND tablename <> 'flyway_schema_history'
                  AND NOT (has_table_privilege(?::text, format('%I.%I', schemaname, tablename), 'SELECT')
                       AND has_table_privilege(?::text, format('%I.%I', schemaname, tablename), 'INSERT'))""", String.class, ROLE, ROLE);
        assertThat(missing).isEmpty();
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(?::text, 'public.audit_event', 'UPDATE')", Boolean.class, ROLE)).isFalse();
        assertThat(jdbc.queryForObject("SELECT has_table_privilege(?::text, 'public.customer_order', 'UPDATE')", Boolean.class, ROLE))
                .as("orders still move through their states").isTrue();
        // Account erasure (V27) runs as this role: it may update these two, and their triggers allow only the erasure.
        for (String table : List.of("case_message", "payment_event")) {
            assertThat(jdbc.queryForObject("SELECT has_table_privilege(?::text, ?::text, 'UPDATE')", Boolean.class, ROLE, "public." + table))
                    .as("erasure can run on %s", table).isTrue();
            assertThat(jdbc.queryForObject("SELECT has_table_privilege(?::text, ?::text, 'DELETE')", Boolean.class, ROLE, "public." + table))
                    .as("no deletion from %s", table).isFalse();
        }
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM (SELECT c.oid FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace
                                      WHERE n.nspname = 'public' AND c.relkind = 'S' OFFSET 0) seq
                WHERE NOT has_sequence_privilege(?::text, seq.oid, 'USAGE')""", Integer.class, ROLE)).isZero();
    }
}
