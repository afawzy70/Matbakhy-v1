package com.matbakhy.app.reliability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.MatbakhyApplication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.PostgresTestConfiguration;
import com.matbakhy.app.support.StorageTestConfiguration;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.notifications.internal.OutboxDispatcher;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * MB-037 / TC-058 / TC-137: recovery after an application restart on real PostgreSQL. Application A places orders (each
 * writes an outbox event in the order transaction); A's worker claims the events and the application stops before
 * finishing them, as a crash would. A fresh application B on the same database cannot take the events while A's lease
 * is live, reclaims them when it expires, and delivers each notification exactly once; a second pass changes nothing,
 * and the orders are intact. The containers outlive both applications, so the database really survives the restart.
 */
class RestartRecoveryIntegrationTest {

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(PostgresTestConfiguration.image());
    static GenericContainer<?> storage;

    @BeforeAll
    static void startInfrastructure() {
        POSTGRES.start();
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

    private static ConfigurableApplicationContext boot() {
        // Command-line arguments outrank application.yml (builder default properties would not).
        return new SpringApplicationBuilder(MatbakhyApplication.class).run(
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--matbakhy.storage.endpoint=http://" + storage.getHost() + ":" + storage.getMappedPort(9090),
                "--matbakhy.environment=CI",
                "--server.port=0",
                "--matbakhy.orders.expiry-job.enabled=false",
                "--matbakhy.finance.posting-job.enabled=false",
                "--matbakhy.notifications.jobs-enabled=false",
                "--matbakhy.notifications.lease=3s");
    }

    private static MockHttpServletResponse call(MockMvc mvc, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder r,
                                                com.matbakhy.app.security.AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    @Test
    void events_claimed_by_a_stopped_application_are_delivered_exactly_once_after_restart() throws Exception {
        List<UUID> orders = new ArrayList<>();
        String customerSubject = UUID.randomUUID().toString();
        UUID customer;

        // ---- application A: real orders, then a worker claims their events and A stops mid-work ----
        try (ConfigurableApplicationContext a = boot()) {
            JdbcTemplate jdbc = a.getBean(JdbcTemplate.class);
            JsonMapper json = a.getBean(JsonMapper.class);
            TestLogins logins = a.getBean(TestLogins.class);
            MockMvc mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) a).apply(springSecurity()).build();
            TestCommerce fx = new TestCommerce(jdbc, a.getBean(Clock.class));
            fx.activatePolicy(0);
            customer = logins.login(customerSubject, "Customer").getPrincipal().id();
            fx.verifyPhone(customer);
            UUID cook = logins.login(UUID.randomUUID().toString(), "Cook").getPrincipal().id();
            UUID kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
            UUID zone = fx.zone(3000);
            fx.deliversTo(kitchen, zone);
            UUID meal = fx.meal(kitchen, 12000, "فتة", "Fatta");
            UUID slot = fx.slot(kitchen, 300, 20, meal, 10);
            UUID address = fx.address(customer, zone);
            for (int i = 0; i < 3; i++) {
                MockHttpServletResponse quote = call(mvc, post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\""
                        + slot + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), logins.login(customerSubject, "Customer"));
                assertThat(quote.getStatus()).as(quote.getContentAsString()).isEqualTo(201);
                MockHttpServletResponse placed = call(mvc, post("/api/v1/orders").header("Idempotency-Key", "rst-" + UUID.randomUUID()).content("""
                        {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                        .formatted(json.readTree(quote.getContentAsString()).get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS,
                                TestCommerce.CANCELLATION)), logins.login(customerSubject, "Customer"));
                assertThat(placed.getStatus()).as(placed.getContentAsString()).isEqualTo(201);
                orders.add(UUID.fromString(json.readTree(placed.getContentAsString()).get("id").asString()));
            }
            // A's worker takes the lease on every pending event (the claim the dispatcher makes) and A then stops before
            // processing them, as if the process were killed between claim and commit.
            int claimed = jdbc.update("""
                    UPDATE outbox_event SET lease_owner = 'worker-A', leased_until = now() + interval '3 seconds'
                    WHERE status = 'PENDING' AND aggregate_id = ANY (?)""", (Object) orders.toArray(UUID[]::new));
            assertThat(claimed).as("one outbox event per order").isEqualTo(3);
        }

        // ---- application B: a fresh process on the same database ----
        try (ConfigurableApplicationContext b = boot()) {
            JdbcTemplate jdbc = b.getBean(JdbcTemplate.class);
            OutboxDispatcher dispatcher = b.getBean(OutboxDispatcher.class);
            Object[] ids = {orders.toArray(UUID[]::new)};
            assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_order WHERE id = ANY (?)", Integer.class, ids)).as("orders survived the restart").isEqualTo(3);
            dispatcher.dispatchOnce("worker-B", 100);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ANY (?) AND status = 'SENT'", Integer.class, ids))
                    .as("a live lease of the stopped worker is respected").isZero();
            Thread.sleep(3500);
            int processed = 0;
            for (int i = 0; i < 5; i++) {
                processed += dispatcher.dispatchOnce("worker-B", 100);
            }
            assertThat(processed).isGreaterThanOrEqualTo(3);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE aggregate_id = ANY (?) AND status = 'SENT'", Integer.class, ids))
                    .as("all claimed events delivered after the lease expired").isEqualTo(3);
            List<Integer> perRecipient = jdbc.queryForList(
                    "SELECT count(*) FROM notification WHERE order_id = ANY (?) GROUP BY recipient_id, order_id, type", Integer.class, ids);
            assertThat(perRecipient).as("exactly one notification per recipient, order and type").isNotEmpty().containsOnly(1);
            assertThat(jdbc.queryForObject("SELECT count(DISTINCT order_id) FROM notification WHERE order_id = ANY (?)", Integer.class, ids))
                    .as("every order notified").isEqualTo(3);
            assertThat(dispatcher.dispatchOnce("worker-B", 100)).as("a second pass changes nothing").isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM stock_allocation WHERE order_id = ANY (?)", Integer.class, ids)).isEqualTo(3);
        }
    }
}
