package com.matbakhy.app.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.notifications.internal.OutboxDispatcher;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.shared.web.RequestContext;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MB-039 audit and log readiness, local part (docs/operations/05-observability-readiness.md, AUD-01 and LOG-01): the
 * gaps found by the audit coverage review are tested here on PostgreSQL with the deployed log format (ECS JSON).
 * Deployed checks remain MB-040.
 */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.structured.format.console=ecs")
@Import(TestLogins.class)
class AuditAndCorrelationReadinessIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired AuditLog audit;
    @Autowired TransactionTemplate tx;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired JsonMapper json;

    /** A denial on a long path must still be refused and audited (the action column holds at most 120 characters). */
    @Test
    void a_denied_request_on_a_long_path_is_refused_and_audited() throws Exception {
        AccountAuthentication customer = logins.login(UUID.randomUUID().toString(), "Customer");
        String path = "/internal/" + "x".repeat(3000);
        int status = mvc.perform(get(path).with(authentication(customer))).andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(403);
        List<Map<String, Object>> rows = jdbc.queryForList("SELECT action, object_id, outcome FROM audit_event WHERE actor_id = ?",
                customer.getPrincipal().id());
        assertThat(rows).hasSize(1);
        assertThat((String) rows.getFirst().get("action")).startsWith("denied GET /internal/").hasSizeLessThanOrEqualTo(120);
        assertThat((String) rows.getFirst().get("object_id")).startsWith("/internal/").hasSizeLessThanOrEqualTo(512);
        assertThat(rows.getFirst().get("outcome")).isEqualTo("DENIED");
    }

    @Test
    void the_audit_table_rejects_truncate_as_well_as_update_and_delete() {
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_event")).hasMessageContaining("append-only");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM pg_trigger WHERE tgrelid = 'audit_event'::regclass AND NOT tgisinternal AND tgenabled <> 'D'",
                Integer.class)).as("the append-only triggers are enabled").isGreaterThanOrEqualTo(2);
    }

    /** Business-success audit rows join the caller's transaction: a rolled-back change leaves no audit row claiming it. */
    @Test
    void an_audit_row_written_in_a_rolled_back_transaction_does_not_survive() {
        UUID actor = logins.login(UUID.randomUUID().toString(), "Staff").getPrincipal().id();
        String object = UUID.randomUUID().toString();
        assertThatThrownBy(() -> tx.executeWithoutResult(s -> {
            audit.record(new AuditLog.Entry(actor, "config.change", null, "config", object, AuditLog.Outcome.ALLOWED, "readiness check", "trace-rb", Map.of()));
            throw new IllegalStateException("business change failed after the audit write");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE object_id = ?", Integer.class, object)).isZero();
    }

    /** LOG-01: a request's MDC entry is removed when the request ends, so the next work on the thread carries no stale ID. */
    @Test
    void the_request_id_does_not_outlive_its_request() throws Exception {
        AccountAuthentication customer = logins.login(UUID.randomUUID().toString(), "Customer");
        MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
        mvc.perform(get("/api/v1/me").header("X-Request-Id", "trace-mdc-0001").with(authentication(customer))).andReturn();
        assertThat(MDC.get(RequestContext.REQUEST_ID_MDC_KEY)).isNull();
    }

    /**
     * LOG-01: an outbox dispatch failure is logged under the request ID stored with the event, and the worker thread's own
     * MDC is restored afterwards (kept when it had one, empty when it had none). The failure is forced by a test-only
     * trigger on this one event.
     */
    @Test
    void an_outbox_dispatch_failure_carries_the_stored_request_id_and_restores_the_worker_mdc(CapturedOutput output) throws Exception {
        UUID event = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_event (id, aggregate_type, aggregate_id, event_type, payload, available_at, created_at, request_id)
                VALUES (?, 'ORDER', ?, 'ORDER_PLACED', '{}'::jsonb, now() - interval '1 minute', now() - interval '1 minute', 'trace-outbox-0003')""",
                event, UUID.randomUUID());
        jdbc.execute("""
                CREATE OR REPLACE FUNCTION mb039_fail_outbox() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                    IF NEW.id = '%s'::uuid AND NEW.status = 'SENT' THEN RAISE EXCEPTION 'forced dispatch failure'; END IF;
                    RETURN NEW;
                END $$""".formatted(event));
        jdbc.execute("CREATE TRIGGER mb039_fail_outbox BEFORE UPDATE ON outbox_event FOR EACH ROW EXECUTE FUNCTION mb039_fail_outbox()");
        try {
            MDC.put(RequestContext.REQUEST_ID_MDC_KEY, "worker-own-context");
            dispatcher.dispatchOnce("mb039-worker", 100);
            assertThat(MDC.get(RequestContext.REQUEST_ID_MDC_KEY)).as("the worker's previous MDC value is restored").isEqualTo("worker-own-context");

            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
            jdbc.update("UPDATE outbox_event SET available_at = now() - interval '1 minute' WHERE id = ?", event);
            dispatcher.dispatchOnce("mb039-worker", 100);
            assertThat(MDC.get(RequestContext.REQUEST_ID_MDC_KEY)).as("no request ID is left behind on a worker without one").isNull();
        } finally {
            MDC.remove(RequestContext.REQUEST_ID_MDC_KEY);
            jdbc.execute("DROP TRIGGER mb039_fail_outbox ON outbox_event");
            jdbc.execute("DROP FUNCTION mb039_fail_outbox()");
        }
        assertThat(jdbc.queryForObject("SELECT attempts FROM outbox_event WHERE id = ?", Integer.class, event)).isEqualTo(2);

        List<JsonNode> lines = new ArrayList<>();
        for (String line : output.getAll().split("\\R")) {
            if (line.startsWith("{")) {
                lines.add(json.readTree(line));
            }
        }
        List<JsonNode> failures = lines.stream().filter(l -> l.path("message").asString().startsWith("outbox event " + event + " dispatch failed")).toList();
        assertThat(failures).hasSize(2).allSatisfy(l -> assertThat(l.path("requestId").asString()).isEqualTo("trace-outbox-0003"));
        // LOG-01 stable fields: service name, environment and version on every structured line.
        assertThat(failures).allSatisfy(l -> {
            JsonNode service = l.path("service");
            String name = service.isObject() ? service.path("name").asString() : l.path("service.name").asString();
            String environment = service.isObject() ? service.path("environment").asString() : l.path("service.environment").asString();
            String version = service.isObject() ? service.path("version").asString() : l.path("service.version").asString();
            assertThat(name).isEqualTo("matbakhy-api");
            assertThat(environment).isNotBlank();
            assertThat(version).isNotBlank();
        });
    }
}
