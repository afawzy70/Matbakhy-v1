package com.matbakhy.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
 * MB-023 backend (TC-123): staff moderation hides a meal from discovery, detail and checkout without touching the
 * cook's content; suspension and restore are reflected in discovery and checkout and report the active-order impact.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class ModerationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    String moderatorSubject;
    String reviewerSubject;
    UUID kitchen;
    UUID zone;
    UUID meal;
    UUID slot;
    UUID address;
    String token;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        UUID customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        cookSubject = UUID.randomUUID().toString();
        UUID cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        moderatorSubject = staff("content.moderate");
        reviewerSubject = staff("cook.review");
        kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(reviewerSubject, "Reviewer").getPrincipal().id());
        zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        token = "mod" + UUID.randomUUID().toString().substring(0, 8);
        meal = fx.meal(kitchen, 9000, "ملوخية " + token, "Molokhia " + token);
        slot = fx.slot(kitchen, 300, 20, meal, 10);
        address = fx.address(customer, zone);
    }

    private String staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        return subject;
    }

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "User");
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private long mealVersion() {
        return jdbc.queryForObject("SELECT version FROM meal WHERE id = ?", Long.class, meal);
    }

    private MockHttpServletResponse moderate(String who, String action, String reason, long version) throws Exception {
        String r = reason == null ? "" : ",\"reason\":\"" + reason + "\"";
        return call(post("/api/v1/ops/meals/" + meal + "/moderation").content("{\"action\":\"" + action + "\"" + r + ",\"expectedVersion\":" + version + "}"),
                as(who));
    }

    private boolean discoverable() throws Exception {
        String body = mvc.perform(get("/api/v1/meals").param("q", token)).andReturn().getResponse().getContentAsString();
        return body.contains(meal.toString());
    }

    private int quoteStatus() throws Exception {
        return call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), as(customerSubject)).getStatus();
    }

    private int publicDetailStatus() throws Exception {
        return mvc.perform(get("/api/v1/meals/" + meal)).andReturn().getResponse().getStatus();
    }

    @Test
    void hiding_a_meal_removes_it_from_discovery_detail_and_checkout_until_restored() throws Exception {
        assertThat(discoverable()).isTrue();
        assertThat(publicDetailStatus()).isEqualTo(200);
        assertThat(quoteStatus()).isEqualTo(201);

        assertThat(moderate(cookSubject, "HIDE", "not allowed", mealVersion()).getStatus()).as("cook").isEqualTo(403);
        assertThat(moderate(reviewerSubject, "HIDE", "wrong permission", mealVersion()).getStatus()).isEqualTo(403);
        assertThat(moderate(moderatorSubject, "HIDE", null, mealVersion()).getStatus()).as("reason required").isEqualTo(422);
        assertThat(moderate(moderatorSubject, "HIDE", "misleading photo", mealVersion() + 1).getStatus()).isEqualTo(409);
        String contentBefore = jdbc.queryForObject("SELECT title_ar || status || revision FROM meal WHERE id = ?", String.class, meal);

        JsonNode hidden = ok(moderate(moderatorSubject, "HIDE", "misleading photo", mealVersion()), 200);
        assertThat(hidden.get("moderationState").asString()).isEqualTo("HIDDEN");
        assertThat(hidden.get("moderationReason").asString()).isEqualTo("misleading photo");
        assertThat(hidden.get("status").asString()).as("cook publication status unchanged").isEqualTo("PUBLISHED");
        assertThat(jdbc.queryForObject("SELECT title_ar || status || revision FROM meal WHERE id = ?", String.class, meal)).isEqualTo(contentBefore);
        assertThat(discoverable()).isFalse();
        assertThat(publicDetailStatus()).isEqualTo(404);
        assertThat(quoteStatus()).isNotEqualTo(201);
        assertThat(moderate(moderatorSubject, "HIDE", "again", mealVersion()).getStatus()).isEqualTo(409);

        JsonNode cookView = ok(call(get("/api/v1/cook/meals"), as(cookSubject)), 200);
        assertThat(cookView.get("items").get(0).get("moderationState").asString()).as("cook sees the hide and reason").isEqualTo("HIDDEN");
        // The cook cannot override the hide by pausing and republishing.
        UUID photo = com.matbakhy.app.support.TestUploads.approvedMealPhoto(mvc, json, as(cookSubject));
        jdbc.update("INSERT INTO meal_media (meal_id, media_id, position) VALUES (?, ?, 0)", meal, photo);
        ok(call(post("/api/v1/cook/meals/" + meal + "/publication").content("{\"action\":\"PAUSE\",\"expectedVersion\":" + mealVersion() + "}"),
                as(cookSubject)), 200);
        ok(call(post("/api/v1/cook/meals/" + meal + "/publication").content("{\"action\":\"PUBLISH\",\"expectedVersion\":" + mealVersion() + "}"),
                as(cookSubject)), 200);
        assertThat(discoverable()).isFalse();

        JsonNode list = ok(call(get("/api/v1/ops/catalogue").param("moderationState", "HIDDEN").param("q", token), as(moderatorSubject)), 200);
        assertThat(list.get("items").size()).isEqualTo(1);
        assertThat(list.get("items").get(0).get("kitchenName").asString()).isNotBlank();
        assertThat(list.toString()).doesNotContain("Synthetic fixture location").doesNotContain("+201001234567");
        assertThat(call(get("/api/v1/ops/catalogue"), as(reviewerSubject)).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/catalogue").param("moderationState", "GONE"), as(moderatorSubject)).getStatus()).isEqualTo(422);

        JsonNode restored = ok(moderate(moderatorSubject, "RESTORE", "photo replaced", mealVersion()), 200);
        assertThat(restored.get("moderationState").asString()).isEqualTo("VISIBLE");
        assertThat(restored.get("moderationReason").isNull()).isTrue();
        assertThat(discoverable()).isTrue();
        assertThat(publicDetailStatus()).isEqualTo(200);
        assertThat(quoteStatus()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'meal.moderation' AND object_id = ?", Integer.class,
                meal.toString())).isEqualTo(2);
    }

    @Test
    void suspension_and_restore_are_reflected_in_discovery_and_checkout_with_the_active_order_impact() throws Exception {
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":2}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), as(customerSubject)), 201);
        ok(call(post("/api/v1/orders").header("Idempotency-Key", "mod-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), as(customerSubject)), 201);

        long v = jdbc.queryForObject("SELECT version FROM kitchen WHERE id = ?", Long.class, kitchen);
        JsonNode suspended = ok(call(post("/api/v1/ops/kitchens/" + kitchen + "/operating-state").content(
                "{\"action\":\"SUSPEND\",\"reason\":\"complaint review\",\"expectedVersion\":" + v + "}"), logins.loginWithMfa(reviewerSubject, "Reviewer")), 200);
        assertThat(suspended.get("operatingState").asString()).isEqualTo("SUSPENDED");
        assertThat(suspended.get("activeOrders").get("PENDING_ACCEPTANCE").asInt()).as("impact summary").isEqualTo(1);
        assertThat(discoverable()).isFalse();
        assertThat(publicDetailStatus()).isEqualTo(404);
        assertThat(quoteStatus()).isNotEqualTo(201);

        JsonNode restored = ok(call(post("/api/v1/ops/kitchens/" + kitchen + "/operating-state").content(
                "{\"action\":\"RESTORE\",\"reason\":\"review closed\",\"expectedVersion\":" + suspended.get("version").asLong() + "}"),
                logins.loginWithMfa(reviewerSubject, "Reviewer")), 200);
        assertThat(restored.get("operatingState").asString()).isEqualTo("ACTIVE");
        assertThat(restored.get("activeOrders").get("PENDING_ACCEPTANCE").asInt()).isEqualTo(1);
        assertThat(discoverable()).isTrue();
        assertThat(quoteStatus()).isEqualTo(201);
    }
}
