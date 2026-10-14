package com.matbakhy.app.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.MatbakhyApplication;
import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.PostgresTestConfiguration;
import com.matbakhy.app.support.StorageTestConfiguration;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.finance.internal.LedgerPostingJob;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MB-039 / TC-057, local rehearsal only (the staging rehearsal needs O07): orders in several states, stock and a
 * posted ledger are backed up with {@code scripts/db-backup.sh}, restored into a new database with
 * {@code scripts/db-restore.sh}, which reconciles them with {@code infra/db/reconcile.sql}; the application then starts
 * on the restored database (migration history validated) and serves the restored orders. The reconciliation is shown to
 * catch damaged stock and ledger data, and the restore refuses a non-empty target and a corrupted file. Measured times
 * are printed for the RPO/RTO record.
 */
class BackupRestoreIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestConfiguration.image());
    static GenericContainer<?> storage;

    @BeforeAll
    static void startInfrastructure() {
        POSTGRES.start();
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("../../scripts/db-backup.sh").toAbsolutePath(), 0755), "/work/db-backup.sh");
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("../../scripts/db-restore.sh").toAbsolutePath(), 0755), "/work/db-restore.sh");
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(Path.of("../../infra/db/reconcile.sql").toAbsolutePath()), "/work/reconcile.sql");
        storage = new GenericContainer<>(DockerImageName.parse(System.getProperty("matbakhy.test.s3mock-image")))
                .withEnv("COM_ADOBE_TESTING_S3MOCK_STORE_INITIAL_BUCKETS", StorageTestConfiguration.PRIVATE_BUCKET + "," + StorageTestConfiguration.PUBLIC_BUCKET)
                .withExposedPorts(9090)
                .waitingFor(Wait.forHttp("/").forPort(9090).forStatusCode(200));
        storage.start();
    }

    @AfterAll
    static void stopInfrastructure() {
        storage.stop();
        POSTGRES.stop();
    }

    private static ConfigurableApplicationContext boot(String database) {
        String url = POSTGRES.getJdbcUrl().replace("/" + POSTGRES.getDatabaseName() + "?", "/" + database + "?");
        return new SpringApplicationBuilder(MatbakhyApplication.class).run(
                "--spring.datasource.url=" + url,
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--matbakhy.storage.endpoint=http://" + storage.getHost() + ":" + storage.getMappedPort(9090),
                "--matbakhy.environment=CI",
                "--server.port=0",
                "--matbakhy.orders.expiry-job.enabled=false",
                "--matbakhy.finance.posting-job.interval=1h",
                "--matbakhy.notifications.jobs-enabled=false",
                "--matbakhy.privacy.erasure-job.enabled=false");
    }

    /** Runs a command inside the database container with libpq pointed at the given database. */
    private static ExecResult sh(String database, String command) throws Exception {
        return POSTGRES.execInContainer("sh", "-c", "export PGUSER='" + POSTGRES.getUsername() + "' PGDATABASE='" + database + "'; " + command);
    }

    private static ExecResult psql(String database, String sql) throws Exception {
        return sh(database, "psql -X -At -v ON_ERROR_STOP=1 -c \"" + sql + "\"");
    }

    private static MockHttpServletResponse call(MockMvc mvc, MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private static JsonNode ok(JsonMapper json, MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    @Test
    void backup_is_accepted_only_after_restore_and_reconciliation_and_the_app_runs_on_the_restored_database() throws Exception {
        String source = POSTGRES.getDatabaseName();
        String customerSubject = UUID.randomUUID().toString();
        UUID pending;
        UUID completed;
        UUID rejected;
        UUID deposit;

        // ---- live system: orders in several states, stock movements and a posted commission ----
        try (ConfigurableApplicationContext app = boot(source)) {
            JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
            JsonMapper json = app.getBean(JsonMapper.class);
            TestLogins logins = app.getBean(TestLogins.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).apply(springSecurity()).build();
            TestCommerce fx = new TestCommerce(jdbc, app.getBean(Clock.class));
            fx.activatePolicy(0);
            AccountAuthentication customer = logins.login(customerSubject, "Customer");
            fx.verifyPhone(customer.getPrincipal().id());
            AccountAuthentication cook = logins.login(UUID.randomUUID().toString(), "Cook");
            UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
            UUID kitchen = TestKitchens.approvedKitchen(jdbc, cook.getPrincipal().id(), reviewer);
            UUID meal = fx.meal(kitchen, 12000, "فتة", "Fatta");
            UUID slot = fx.slot(kitchen, 300, 20, meal, 10);

            UUID[] ids = new UUID[3];
            for (int i = 0; i < 3; i++) {
                JsonNode quote = ok(json, call(mvc, post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":" + (i + 1)
                        + "}],\"slotId\":\"" + slot + "\",\"fulfillmentMode\":\"PICKUP\"}"), customer), 201);
                JsonNode order = ok(json, call(mvc, post("/api/v1/orders").header("Idempotency-Key", "bkp-" + UUID.randomUUID()).content("""
                        {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                        .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), customer), 201);
                ids[i] = UUID.fromString(order.get("id").asString());
            }
            pending = ids[0];
            completed = ids[1];
            rejected = ids[2];
            // A deposit order (D16, found in the local staging rehearsal): the cash still due is the remainder, not the total, and the
            // payment history must survive the restore. Reconciliation has to accept it.
            jdbc.update("""
                    INSERT INTO kitchen_payment_profile (kitchen_id, deposit_bps, cod_max_total_minor, recipient_method, recipient_identifier,
                                                         recipient_name, recipient_status, approved_by, approved_at)
                    VALUES (?, 5000, NULL, 'INSTAPAY', 'synthetic.kitchen@instapay', 'Synthetic Kitchen', 'APPROVED', ?, now())""", kitchen, reviewer);
            JsonNode depositQuote = ok(json, call(mvc, post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\""
                    + slot + "\",\"fulfillmentMode\":\"PICKUP\",\"paymentMethod\":\"DEPOSIT\"}"), customer), 201);
            JsonNode depositOrder = ok(json, call(mvc, post("/api/v1/orders").header("Idempotency-Key", "bkp-" + UUID.randomUUID()).content("""
                    {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                    .formatted(depositQuote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), customer), 201);
            deposit = UUID.fromString(depositOrder.get("id").asString());
            ok(json, call(mvc, post("/api/v1/orders/" + deposit + "/payment/transfer-report").content("{}"), customer), 200);
            for (String step : new String[] {"accept", "prepare", "ready"}) {
                cook(mvc, json, jdbc, cook, completed, step, "");
            }
            String code = ok(json, call(mvc, get("/api/v1/orders/" + completed), customer), 200).get("handoverCode").asString();
            cook(mvc, json, jdbc, cook, completed, "handover", ",\"code\":\"" + code + "\"");
            cook(mvc, json, jdbc, cook, rejected, "reject", ",\"reasonCode\":\"KITCHEN_BUSY\"");
            assertThat(app.getBean(LedgerPostingJob.class).postPending()).as("commission posted for the completed order").isEqualTo(1);
        }

        ExecResult live = sh(source, "psql -X -At -F ' ' -v ON_ERROR_STOP=1 -f /work/reconcile.sql");
        assertThat(live.getExitCode()).as(live.getStderr()).isZero();
        assertThat(live.getStdout().lines().toList()).as("the live database reconciles").hasSize(10).allSatisfy(l -> assertThat(l).endsWith(" 0"));

        // ---- backup ----
        long backupStart = System.nanoTime();
        ExecResult backup = sh(source, "/work/db-backup.sh /backups");
        long backupMillis = (System.nanoTime() - backupStart) / 1_000_000;
        assertThat(backup.getExitCode()).as(backup.getStdout() + backup.getStderr()).isZero();
        String dump = psqlLess(sh(source, "ls /backups/*.dump").getStdout().strip());
        assertThat(sh(source, "cat " + dump.replace(".dump", ".manifest")).getStdout())
                .contains("schema_version=28", "rows.customer_order=4", "rows.ledger_group=1", "rows.ledger_entry=2");

        // Written after the backup: lost on restore. That window is the RPO of a backup-only recovery.
        psql(source, "UPDATE meal_slot SET capacity = capacity + 1");

        // ---- restore into a new database: reconciled ----
        long restoreStart = System.nanoTime();
        ExecResult restore = sh(source, "RESTORE_CONFIRM_TARGET=matbakhy_restored /work/db-restore.sh " + dump + " matbakhy_restored /work/reconcile.sql");
        assertThat(restore.getExitCode()).as(restore.getStdout() + restore.getStderr()).isZero();
        assertThat(restore.getStdout()).contains("OK ledger_groups_balanced", "OK meal_slot_counters_match_allocations", "RECONCILED matbakhy_restored")
                .doesNotContain("FAIL", "MISMATCH");

        // ---- the application starts on the restored database and serves the restored orders ----
        try (ConfigurableApplicationContext app = boot("matbakhy_restored")) {
            long rtoMillis = (System.nanoTime() - restoreStart) / 1_000_000;
            JdbcTemplate jdbc = app.getBean(JdbcTemplate.class);
            JsonMapper json = app.getBean(JsonMapper.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).apply(springSecurity()).build();
            AccountAuthentication customer = app.getBean(TestLogins.class).login(customerSubject, "Customer");
            assertThat(ok(json, call(mvc, get("/api/v1/orders/" + completed), customer), 200).get("state").asString()).isEqualTo("COMPLETED");
            assertThat(ok(json, call(mvc, get("/api/v1/orders/" + pending), customer), 200).get("state").asString()).isEqualTo("PENDING_ACCEPTANCE");
            assertThat(ok(json, call(mvc, get("/api/v1/orders/" + rejected), customer), 200).get("state").asString()).isEqualTo("REJECTED");
            JsonNode restoredDeposit = ok(json, call(mvc, get("/api/v1/orders/" + deposit), customer), 200).get("payment");
            assertThat(restoredDeposit.get("state").asString()).as("the payment history survived the restore").isEqualTo("CUSTOMER_TRANSFER_REPORTED");
            assertThat(restoredDeposit.get("deposit").get("amountMinor").asLong()).isEqualTo(restoredDeposit.get("remainder").get("amountMinor").asLong());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE success", Integer.class))
                    .as("no migration re-applied on start").isEqualTo(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history", Integer.class));
            assertThat(jdbc.queryForObject("SELECT sum(capacity) FROM meal_slot", Integer.class)).as("the post-backup write is not in the restore").isEqualTo(10);
            System.out.printf("TC-057 local rehearsal: backup %d ms; restore + reconcile + application start %d ms; "
                    + "RPO = time since the last accepted backup (a write made after it was lost, as expected)%n", backupMillis, rtoMillis);
        }

        // ---- reconciliation catches damage ----
        assertThat(sh(source, "RESTORE_CONFIRM_TARGET=matbakhy_damaged /work/db-restore.sh " + dump + " matbakhy_damaged /work/reconcile.sql").getExitCode()).isZero();
        psql("matbakhy_damaged", "UPDATE meal_slot SET reserved = reserved + 1 WHERE id = (SELECT id FROM meal_slot LIMIT 1)");
        ExecResult payDamage = psql("matbakhy_damaged", "UPDATE order_payment SET state = 'DISPUTED' WHERE method = 'DEPOSIT'");
        assertThat(payDamage.getExitCode()).as(payDamage.getStderr()).isZero();
        psql("matbakhy_damaged", "ALTER TABLE ledger_entry DISABLE TRIGGER USER; "
                + "INSERT INTO ledger_entry (id, group_id, account, direction, amount_minor) "
                + "SELECT gen_random_uuid(), id, 'COOK_COMMISSION_RECEIVABLE', 'DEBIT', 1 FROM ledger_group LIMIT 1");
        ExecResult damaged = sh("matbakhy_damaged", "psql -X -At -F ' ' -v ON_ERROR_STOP=1 -f /work/reconcile.sql");
        assertThat(damaged.getStdout()).contains("meal_slot_counters_match_allocations 1", "cook_slot_counters_match_meal_slots 1", "ledger_groups_balanced 1",
                "payment_state_matches_history 1");

        // ---- refusals ----
        ExecResult overExisting = sh(source, "RESTORE_CONFIRM_TARGET=matbakhy_restored /work/db-restore.sh " + dump + " matbakhy_restored /work/reconcile.sql");
        assertThat(overExisting.getExitCode()).isEqualTo(2);
        assertThat(overExisting.getStderr()).contains("restore only into a new, empty database");
        ExecResult unconfirmed = sh(source, "/work/db-restore.sh " + dump + " matbakhy_other /work/reconcile.sql");
        assertThat(unconfirmed.getExitCode()).isEqualTo(2);
        sh(source, "cp " + dump + " /backups/corrupt.dump && cp " + dump + ".sha256 /backups/corrupt.dump.sha256"
                + " && sed -i 's#  .*#  corrupt.dump#' /backups/corrupt.dump.sha256 && printf x >> /backups/corrupt.dump");
        ExecResult corrupt = sh(source, "RESTORE_CONFIRM_TARGET=matbakhy_corrupt /work/db-restore.sh /backups/corrupt.dump matbakhy_corrupt /work/reconcile.sql");
        assertThat(corrupt.getExitCode()).isEqualTo(2);
        assertThat(corrupt.getStderr()).contains("checksum mismatch");
        assertThat(psql(source, "SELECT count(*) FROM pg_database WHERE datname IN ('matbakhy_other', 'matbakhy_corrupt')").getStdout().strip())
                .as("refused restores create nothing").isEqualTo("0");
    }

    private static String psqlLess(String s) {
        assertThat(s).as("exactly one backup").doesNotContain("\n").endsWith(".dump");
        return s;
    }

    private static void cook(MockMvc mvc, JsonMapper json, JdbcTemplate jdbc, AccountAuthentication cook, UUID order, String action, String extra)
            throws Exception {
        long version = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
        ok(json, call(mvc, post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", "bkp-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + version + extra + "}"), cook), 200);
    }
}
