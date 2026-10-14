package com.matbakhy.app.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matbakhy.app.support.IntegrationTest;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

/** TC-106: a blank PostgreSQL migrates and baseline constraints reject invalid or duplicate rows. */
@IntegrationTest
class MigrationIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private UUID user(String issuer, String subject) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user (id, issuer, subject) VALUES (?, ?, ?)", id, issuer, subject);
        return id;
    }

    private static String uniqueIssuer() {
        return "https://idp.test.invalid/" + UUID.randomUUID();
    }

    @Test
    void blank_database_is_migrated_by_flyway_to_the_baseline() {
        List<String> versions = jdbc.queryForList(
                "SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank", String.class);
        // Every forward migration shipped on the classpath was applied successfully, in order.
        List<String> shipped;
        try {
            shipped = java.util.Arrays.stream(new org.springframework.core.io.support.PathMatchingResourcePatternResolver()
                            .getResources("classpath:db/migration/V*__*.sql"))
                    .map(r -> r.getFilename().substring(1, r.getFilename().indexOf("__")))
                    .sorted(java.util.Comparator.comparingInt(Integer::parseInt)).toList();
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
        assertThat(shipped).startsWith("1", "2", "3");
        assertThat(versions).isEqualTo(shipped);
        assertThat(jdbc.queryForObject("SELECT current_setting('server_version_num')::int / 10000", Integer.class))
                .isEqualTo(16);
        assertThat(jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name",
                String.class)).contains("app_user", "idempotency_record", "spring_session", "spring_session_attributes",
                "service_zone", "address", "phone_challenge", "deletion_request");
    }

    @Test
    void duplicate_identity_provider_subject_is_rejected() {
        String issuer = uniqueIssuer();
        user(issuer, "subject-1");
        assertThatThrownBy(() -> user(issuer, "subject-1")).isInstanceOf(DuplicateKeyException.class);
        user(issuer, "subject-2");
    }

    @Test
    void duplicate_idempotency_scope_is_rejected_but_other_actor_or_command_is_allowed() {
        UUID actor = user(uniqueIssuer(), "s");
        UUID other = user(uniqueIssuer(), "s");
        String sql = "INSERT INTO idempotency_record (actor_id, command, idempotency_key, request_hash, expires_at) "
                + "VALUES (?, ?, ?, ?, now() + interval '24 hours')";
        String hash = "a".repeat(64);
        jdbc.update(sql, actor, "PLACE_ORDER", "key-00000001", hash);
        assertThatThrownBy(() -> jdbc.update(sql, actor, "PLACE_ORDER", "key-00000001", "b".repeat(64)))
                .isInstanceOf(DuplicateKeyException.class);
        jdbc.update(sql, other, "PLACE_ORDER", "key-00000001", hash);
        jdbc.update(sql, actor, "REPORT_CASH", "key-00000001", hash);
    }

    @Test
    void check_constraints_reject_invalid_values() {
        UUID id = user(uniqueIssuer(), "s");
        assertThatThrownBy(() -> jdbc.update("UPDATE app_user SET locale = 'fr' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE app_user SET phone_e164 = '01001234567' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE app_user SET phone_verified_at = now() WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE app_user SET status = 'UNKNOWN' WHERE id = ?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO idempotency_record (actor_id, command, idempotency_key, request_hash, expires_at) "
                        + "VALUES (?, 'PLACE_ORDER', 'key-00000002', ?, now() + interval '1 hour')", id, "c".repeat(64)))
                .as("idempotency results must be retained for at least 24 hours")
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO idempotency_record (actor_id, command, idempotency_key, request_hash, expires_at) "
                        + "VALUES (?, 'PLACE_ORDER', 'key-00000003', 'not-a-hash', now() + interval '2 days')", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE app_user SET phone_e164 = '+201001234567', phone_verified_at = now() WHERE id = ?", id);
    }

    @Test
    void idempotency_record_requires_an_existing_actor() {
        assertThatThrownBy(() -> jdbc.update(
                "INSERT INTO idempotency_record (actor_id, command, idempotency_key, request_hash, expires_at) "
                        + "VALUES (?, 'PLACE_ORDER', 'key-00000004', ?, now() + interval '2 days')",
                UUID.randomUUID(), "d".repeat(64)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
