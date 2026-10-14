package com.matbakhy.app.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.notifications.internal.OutboxDispatcher;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O07 (2026-10-14): push delivery is prepared behind a feature flag that is off by default. With the flag off the in-app
 * notification and the e-mail delivery still happen, a registered device is kept, and no PUSH delivery is created, so
 * nothing is sent to a push provider and nothing dead-letters. NotificationIntegrationTest covers the flag on.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class PushFlagIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;
    @Autowired OutboxDispatcher dispatcher;

    @Test
    void with_push_delivery_off_the_inbox_still_works_and_no_push_delivery_is_created() throws Exception {
        TestCommerce fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        String customerSubject = UUID.randomUUID().toString();
        UUID customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        String cookSubject = UUID.randomUUID().toString();
        UUID cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        UUID kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id());
        UUID zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        UUID meal = fx.meal(kitchen, 12000, "فتة", "Fatta");
        UUID slot = fx.slot(kitchen, 300, 20, meal, 10);
        UUID address = fx.address(customer, zone);
        AccountAuthentication who = logins.login(customerSubject, "Customer");

        ok(call(put("/api/v1/me/devices/inst-" + UUID.randomUUID()).content("{\"platform\":\"ANDROID\",\"pushToken\":\"synthetic-push-token-"
                + UUID.randomUUID() + "\",\"environment\":\"DEVELOPMENT\",\"appVersion\":\"1.0.0\"}"), who), 200);

        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), who), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", "push-flag-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), who), 201);
        UUID orderId = UUID.fromString(order.get("id").asString());
        long version = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, orderId);
        ok(call(post("/api/v1/cook/orders/" + orderId + "/accept").header("Idempotency-Key", "push-flag-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + version + "}"), logins.login(cookSubject, "Cook")), 200);
        while (dispatcher.dispatchOnce("worker-test", 100) > 0) {
            // drain
        }

        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE order_id = ? AND recipient_id = ? AND type = 'ORDER_ACCEPTED'",
                Integer.class, orderId, customer)).as("the customer's in-app notification").isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                WHERE n.order_id = ? AND n.recipient_id = ? AND d.channel = 'EMAIL'""", Integer.class, orderId, customer))
                .as("e-mail is a separate channel").isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM notification_delivery d JOIN notification n ON n.id = d.notification_id
                WHERE n.order_id = ? AND n.recipient_id = ? AND d.channel = 'PUSH'""", Integer.class, orderId, customer))
                .as("no push delivery while the flag is off").isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM device_registration WHERE user_id = ? AND revoked_at IS NULL", Integer.class, customer))
                .as("the device registration is kept for when push is enabled").isEqualTo(1);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return r.getContentAsString().isEmpty() ? null : json.readTree(r.getContentAsString());
    }
}
