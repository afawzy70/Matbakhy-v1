package com.matbakhy.app.privacy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.matbakhy.app.providers.FakePhoneVerificationSender;
import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.identity.api.PhoneVerificationSender;
import com.matbakhy.notifications.api.NotificationChannels.EmailSender;
import com.matbakhy.notifications.api.NotificationChannels.PushSender;
import com.matbakhy.notifications.api.NotificationChannels.Result;
import com.matbakhy.notifications.internal.DeliveryWorker;
import com.matbakhy.notifications.internal.OutboxDispatcher;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TC-056 on PostgreSQL with the deployed log format (ECS JSON): an OTP, address, order, handover, push-device,
 * provider-failure and unexpected-error flow runs with distinctive synthetic personal data; the captured log output
 * must contain none of it, and one request ID must trace the order request to its later provider failure.
 */
@IntegrationTest
@AutoConfigureMockMvc
@org.springframework.test.context.TestPropertySource(properties = "matbakhy.notifications.push-enabled=true")
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = "logging.structured.format.console=ecs")
@Import({TestLogins.class, LogRedactionIntegrationTest.Beans.class})
class LogRedactionIntegrationTest {

    static final String NAME = "Leakcheck Customer Name";
    static final String EMAIL = "leakcheck.customer@example.invalid";
    static final String PHONE_LOCAL = "01098765432";
    static final String PHONE_E164 = "+201098765432";
    static final String STREET = "Leakcheck Street 9182";
    static final String RECIPIENT = "Leakcheck Recipient";
    static final String PUSH_TOKEN = "leakcheck-push-token-5f3e9a";
    static final String BEARER = "eyJhbGciOiJSUzI1NiJ9.leakcheckpayload.leakchecksignature";
    static final String TRACE = "trace-leak-0001";

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {
        /** Providers that always fail, as during an outage; they see the token but must not cause it to be logged. */
        @Bean
        @Primary
        EmailSender failingEmail() {
            return (deliveryId, accountId, locale, template, params) -> {
                throw new IllegalStateException("provider rejected " + EMAIL + " " + PHONE_E164);
            };
        }

        @Bean
        @Primary
        PushSender failingPush() {
            return (deliveryId, platform, token, template, params) -> new Result(false, false, null, "invalid token " + token);
        }

        @RestController
        static class FailingProbe {
            /** Unexpected failure whose message quotes personal data, as a driver constraint message can. */
            @GetMapping("/api/v1/test-probe/leaky-failure")
            void leaky() {
                throw new IllegalStateException("duplicate key (phone_e164)=(" + PHONE_E164 + ") street " + STREET,
                        new RuntimeException("cause mentions " + EMAIL));
            }
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired PhoneVerificationSender sender;
    @Autowired OutboxDispatcher dispatcher;
    @Autowired DeliveryWorker worker;
    @Autowired MeterRegistry meters;

    @Test
    void captured_logs_hold_no_personal_data_or_secrets_and_one_request_id_traces_order_to_provider_failure(CapturedOutput output)
            throws Exception {
        TestCommerce fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        String customerSubject = UUID.randomUUID().toString();
        AccountAuthentication customer = logins.loginWithEmail(customerSubject, NAME, EMAIL);
        String cookSubject = UUID.randomUUID().toString();
        AccountAuthentication cook = logins.login(cookSubject, "Cook");
        UUID kitchen = TestKitchens.approvedKitchen(jdbc, cook.getPrincipal().id(), logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        UUID zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        UUID meal = fx.meal(kitchen, 12000, "فتة", "Fatta");
        UUID slot = fx.slot(kitchen, 300, 20, meal, 10);

        // OTP: one wrong attempt, then the right code.
        JsonNode challenge = ok(call(post("/api/v1/me/phone/challenges").content("{\"phoneE164\":\"" + PHONE_LOCAL + "\"}"), customer), 201);
        String challengeId = challenge.get("id").asString();
        String otp = ((FakePhoneVerificationSender) sender).lastCode(PHONE_E164).orElseThrow();
        String wrong = otp.equals("000000") ? "111111" : "000000";
        assertThat(call(post("/api/v1/me/phone/challenges/" + challengeId + "/verify").content("{\"code\":\"" + wrong + "\"}"), customer).getStatus())
                .isEqualTo(422);
        ok(call(post("/api/v1/me/phone/challenges/" + challengeId + "/verify").content("{\"code\":\"" + otp + "\"}"), customer), 200);

        // Address and push device.
        JsonNode address = ok(call(post("/api/v1/me/addresses").content("{\"label\":\"Home\",\"recipientName\":\"" + RECIPIENT + "\",\"phone\":\""
                + PHONE_LOCAL + "\",\"governorate\":\"Cairo\",\"city\":\"Cairo\",\"zoneId\":\"" + zone + "\",\"street\":\"" + STREET
                + "\",\"building\":\"12\"}"), customer), 201);
        ok(call(put("/api/v1/me/devices/inst-leakcheck-01").content("{\"platform\":\"ANDROID\",\"pushToken\":\"" + PUSH_TOKEN
                + "\",\"environment\":\"DEVELOPMENT\",\"appVersion\":\"1.0.0\"}"), customer), 200);

        // Order placed under a known request ID, then taken through delivery and a wrong and a right handover code.
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address.get("id").asString() + "\"}"), customer), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", key()).header("X-Request-Id", TRACE).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), customer), 201);
        UUID orderId = UUID.fromString(order.get("id").asString());
        cookCommand(cook, orderId, "accept", "");
        cookCommand(cook, orderId, "prepare", "");
        cookCommand(cook, orderId, "ready", "");
        cookCommand(cook, orderId, "dispatch", ",\"deliveryReference\":\"rider-1\"");
        String handover = ok(call(get("/api/v1/orders/" + orderId), customer), 200).get("handoverCode").asString();
        String wrongHandover = handover.equals("000000") ? "111111" : "000000";
        assertThat(call(post("/api/v1/cook/orders/" + orderId + "/handover").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(orderId) + ",\"code\":\"" + wrongHandover + "\"}"), cook).getStatus()).isEqualTo(422);
        cookCommand(cook, orderId, "handover", ",\"code\":\"" + handover + "\"");

        // Asynchronous delivery against failing providers.
        while (dispatcher.dispatchOnce("leakcheck-worker", 100) > 0) {
            // drain
        }
        worker.deliverOnce("leakcheck-worker", 100);

        // An unexpected failure quoting personal data, and a request carrying a bearer token.
        MockHttpServletResponse failure = call(get("/api/v1/test-probe/leaky-failure").header("X-Request-Id", "trace-leak-0002"), customer);
        assertThat(failure.getStatus()).isEqualTo(500);
        mvc.perform(get("/api/v1/orders").header("Authorization", "Bearer " + BEARER)).andReturn();

        String logs = output.getAll();
        List<String> secrets = List.of(NAME, EMAIL, PHONE_LOCAL, PHONE_E164, "1098765432", STREET, RECIPIENT, PUSH_TOKEN, BEARER, "leakcheckpayload",
                handover, otp);
        for (String secret : secrets) {
            assertThat(logs).as("logs must not contain %s", secret.equals(otp) || secret.equals(handover) ? "a one-time code" : secret)
                    .doesNotContain(secret);
        }

        List<JsonNode> lines = new ArrayList<>();
        for (String line : logs.split("\\R")) {
            if (line.startsWith("{")) {
                lines.add(json.readTree(line));
            }
        }
        assertThat(lines).as("console output is structured JSON").isNotEmpty();
        assertThat(traced(lines, TRACE)).as("the order request is logged under its request ID")
                .anySatisfy(m -> assertThat(m).contains("order " + orderId + " recorded"));
        assertThat(traced(lines, TRACE)).as("the later provider failure carries the same request ID")
                .anySatisfy(m -> assertThat(m).startsWith("notification delivery").contains("failed on attempt 1"));
        assertThat(lines).as("provider-echoed text (the push token) is replaced by a code in logs")
                .anySatisfy(l -> assertThat(l.path("message").asString()).startsWith("notification delivery").contains("PROVIDER_ERROR"));
        assertThat(jdbc.queryForList("""
                SELECT d.last_error FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                WHERE n.order_id = ? AND d.channel = 'PUSH'""", String.class, orderId))
                .as("and in the stored delivery error").isNotEmpty().containsOnly("PROVIDER_ERROR");
        assertThat(lines).as("the unexpected failure is logged with its type and request ID, without its message")
                .anySatisfy(l -> {
                    assertThat(l.path("requestId").asString()).isEqualTo("trace-leak-0002");
                    assertThat(l.path("message").asString()).isEqualTo("Unhandled failure");
                    assertThat(l.path("error").path("stack_trace").asString()).contains("java.lang.IllegalStateException (message redacted)");
                });
    }

    static final String DOC_MARKER = "LEAKCHECK-DOCUMENT-CONTENT-4471";
    static final String DOC_NAME = "leakcheck-national-id.pdf";
    static final String WALLET = "leakcheck.wallet@instapay";
    static final String WALLET_OWNER = "Leakcheck Wallet Owner";
    static final String TRANSFER_REF = "LEAKCHECK-REF-8841";
    static final String VISITOR = "leakcheck-visitor-7f2a91";

    /**
     * TC-056, second part (2026-10-13): private document content and file names, the kitchen's deposit recipient account,
     * the customer's transfer reference (D16) and the anonymous activity visitor id (D17) appear neither in the
     * structured logs nor in any metric name or tag.
     */
    @Test
    void documents_payments_activity_and_metrics_hold_no_personal_data(CapturedOutput output) throws Exception {
        TestCommerce fx = new TestCommerce(jdbc, clock);
        fx.activateLaunchPolicy(0);
        try {
            String customerSubject = UUID.randomUUID().toString();
            AccountAuthentication customer = logins.login(customerSubject, "Customer");
            fx.verifyPhone(customer.getPrincipal().id());
            String cookSubject = UUID.randomUUID().toString();
            AccountAuthentication cook = logins.login(cookSubject, "Cook");
            UUID kitchen = TestKitchens.approvedKitchen(jdbc, cook.getPrincipal().id(), logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
            UUID meal = fx.meal(kitchen, 12000, "فتة", "Fatta");
            UUID slot = fx.slot(kitchen, 300, 20, meal, 10);

            // A private document with a marker in its content and a telling file name, uploaded through a signed URL.
            byte[] doc = ("%PDF-1.4\n1 0 obj << /Type /Catalog /Title (" + DOC_MARKER + ") >> endobj\ntrailer << /Root 1 0 R >>\n%%EOF\n")
                    .getBytes(StandardCharsets.ISO_8859_1);
            String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(doc));
            JsonNode ticket = ok(call(post("/api/v1/uploads").content("""
                    {"purpose":"COOK_DOCUMENT","filename":"%s","contentType":"application/pdf","bytes":%d,"sha256":"%s"}"""
                    .formatted(DOC_NAME, doc.length, sha)), cook), 201);
            HttpResponse<String> put = HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(ticket.get("uploadUrl").asString()))
                    .header("Content-Type", "application/pdf").PUT(HttpRequest.BodyPublishers.ofByteArray(doc)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertThat(put.statusCode()).as(put.body()).isEqualTo(200);
            ok(call(post("/api/v1/uploads/" + ticket.get("uploadId").asString() + "/complete").content("{}"), cook), 200);

            // The kitchen's deposit recipient, approved by finance staff, then a deposit order and the customer's transfer report.
            ok(call(put("/api/v1/cook/payment-profile").content("""
                    {"expectedVersion":0,"depositBps":5000,"codMaxTotalMinor":20000,
                     "recipient":{"method":"INSTAPAY","identifier":"%s","name":"%s"}}""".formatted(WALLET, WALLET_OWNER)), cook), 200);
            String financeSubject = UUID.randomUUID().toString();
            UUID finance = logins.login(financeSubject, "Finance").getPrincipal().id();
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'finance.verify', 'test grant')", UUID.randomUUID(), finance);
            long pv = jdbc.queryForObject("SELECT version FROM kitchen_payment_profile WHERE kitchen_id = ?", Long.class, kitchen);
            ok(call(post("/api/v1/ops/kitchens/" + kitchen + "/payment-recipient/approval").content(
                    "{\"expectedVersion\":" + pv + ",\"reason\":\"recipient name matches the approved cook\"}"), logins.loginWithMfa(financeSubject, "Finance")), 200);
            JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\"" + slot
                    + "\",\"fulfillmentMode\":\"PICKUP\",\"paymentMethod\":\"DEPOSIT\"}"), customer), 201);
            JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", key()).content("""
                    {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                    .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), customer), 201);
            ok(call(post("/api/v1/orders/" + order.get("id").asString() + "/payment/transfer-report").content(
                    "{\"reference\":\"" + TRANSFER_REF + "\"}"), customer), 200);
            while (dispatcher.dispatchOnce("leakcheck-worker-2", 100) > 0) {
                // drain the payment notifications
            }

            // Anonymous activity with a known visitor id.
            assertThat(mvc.perform(post("/api/v1/activity").contentType(MediaType.APPLICATION_JSON).content(
                    "{\"visitorId\":\"" + VISITOR + "\",\"events\":[{\"kind\":\"MEAL_VIEW\",\"subjectId\":\"" + meal + "\"}]}"))
                    .andReturn().getResponse().getStatus()).isEqualTo(202);

            List<String> secrets = List.of(DOC_MARKER, DOC_NAME, "leakcheck-national-id", WALLET, WALLET_OWNER, TRANSFER_REF, VISITOR);
            String logs = output.getAll();
            StringBuilder metricText = new StringBuilder();
            meters.getMeters().forEach(m -> metricText.append(m.getId().getName()).append(m.getId().getTags()).append('\n'));
            for (String secret : secrets) {
                assertThat(logs).as("logs must not contain %s", secret).doesNotContain(secret);
                assertThat(metricText.toString()).as("metric names and tags must not contain %s", secret).doesNotContain(secret);
            }
            // The other test's contact and token values, in case it ran first in this context: metric tags carry none of them.
            for (String secret : List.of(NAME, EMAIL, PHONE_LOCAL, PHONE_E164, STREET, RECIPIENT, PUSH_TOKEN, BEARER)) {
                assertThat(metricText.toString()).as("metric names and tags must not contain %s", secret).doesNotContain(secret);
            }
            assertThat(meters.getMeters()).as("the registry was inspected").isNotEmpty();
        } finally {
            fx.activatePolicy(0);
        }
    }

    private static List<String> traced(List<JsonNode> lines, String requestId) {
        return lines.stream().filter(l -> requestId.equals(l.path("requestId").asString())).map(l -> l.path("message").asString()).toList();
    }

    private void cookCommand(AccountAuthentication cook, UUID order, String action, String extra) throws Exception {
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(order) + extra + "}"), cook), 200);
    }

    private long version(UUID order) {
        return jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return r.getContentAsString().isEmpty() ? null : json.readTree(r.getContentAsString());
    }

    private static String key() {
        return "log-" + UUID.randomUUID();
    }
}
