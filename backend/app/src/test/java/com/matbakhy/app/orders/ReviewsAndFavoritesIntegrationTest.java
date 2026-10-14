package com.matbakhy.app.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

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
 * TC-039 and TC-040 on PostgreSQL: verified-order reviews (owner, completed, once, 1..5), hidden reviews excluded from
 * public lists and ratings, reports, idempotent favorites with unavailable meals annotated, private orders.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class ReviewsAndFavoritesIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;

    TestCommerce fx;
    String customer;
    String other;
    String cook;
    String moderator;
    UUID kitchen;
    UUID meal;
    UUID slot;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customer = UUID.randomUUID().toString();
        fx.verifyPhone(logins.login(customer, "Customer").getPrincipal().id());
        other = UUID.randomUUID().toString();
        fx.verifyPhone(logins.login(other, "Other").getPrincipal().id());
        cook = UUID.randomUUID().toString();
        UUID cookId = logins.login(cook, "Cook").getPrincipal().id();
        moderator = staff("content.moderate");
        kitchen = TestKitchens.approvedKitchen(jdbc, cookId, logins.login(staff("cook.review"), "Reviewer").getPrincipal().id());
        meal = fx.meal(kitchen, 8000, "مكرونة بشاميل " + UUID.randomUUID().toString().substring(0, 6), "Bechamel pasta");
        slot = fx.slot(kitchen, 300, 30, meal, 20);
    }

    private String staff(String... permissions) {
        String subject = UUID.randomUUID().toString();
        UUID id = logins.login(subject, "Staff").getPrincipal().id();
        for (String p : permissions) {
            jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')", UUID.randomUUID(), id, p);
        }
        return subject;
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, String who) throws Exception {
        MockHttpServletRequestBuilder b = r.with(csrf()).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(who == null ? b : b.with(authentication(logins.login(who, "User")))).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return r.getContentAsString().isEmpty() ? null : json.readTree(r.getContentAsString());
    }

    private long version(UUID order) {
        return jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
    }

    private UUID order(String who) throws Exception {
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"PICKUP\"}"), who), 201);
        return UUID.fromString(ok(call(post("/api/v1/orders").header("Idempotency-Key", "rv-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), who), 201).get("id").asString());
    }

    private void complete(UUID order, String who) throws Exception {
        for (String a : new String[] {"accept", "prepare", "ready"}) {
            ok(call(post("/api/v1/cook/orders/" + order + "/" + a).header("Idempotency-Key", "rv-" + UUID.randomUUID())
                    .content("{\"expectedVersion\":" + version(order) + "}"), cook), 200);
        }
        String code = ok(call(get("/api/v1/orders/" + order), who), 200).get("handoverCode").asString();
        ok(call(post("/api/v1/cook/orders/" + order + "/handover").header("Idempotency-Key", "rv-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + version(order) + ",\"code\":\"" + code + "\"}"), cook), 200);
    }

    private MockHttpServletResponse review(UUID order, String who, int rating) throws Exception {
        return call(post("/api/v1/orders/" + order + "/reviews").content("{\"rating\":" + rating + ",\"text\":\"Tasty and hot\"}"), who);
    }

    @Test
    void only_the_owner_of_a_completed_order_reviews_once_and_hidden_reviews_do_not_count() throws Exception {
        UUID pending = order(customer);
        assertThat(review(pending, customer, 5).getStatus()).as("not completed").isEqualTo(409);
        UUID done = order(customer);
        complete(done, customer);
        UUID otherDone = order(other);
        complete(otherDone, other);
        assertThat(review(done, other, 5).getStatus()).as("not the owner").isEqualTo(404);
        assertThat(review(done, customer, 6).getStatus()).isEqualTo(422);
        assertThat(review(done, customer, 0).getStatus()).isEqualTo(422);
        JsonNode created = ok(review(done, customer, 5), 201);
        assertThat(review(done, customer, 4).getStatus()).as("once per order").isEqualTo(409);
        ok(review(otherDone, other, 2), 201);

        JsonNode list = ok(call(get("/api/v1/kitchens/" + kitchen + "/reviews"), null), 200);
        assertThat(list.get("items").size()).isEqualTo(2);
        assertThat(list.get("summary").get("count").asInt()).isEqualTo(2);
        assertThat(list.get("summary").get("averageTenths").asInt()).isEqualTo(35);
        assertThat(list.toString()).doesNotContain(done.toString()).doesNotContain("customer");
        JsonNode detail = ok(call(get("/api/v1/meals/" + meal), null), 200);
        assertThat(detail.get("kitchen").get("ratingSummary").get("count").asInt()).isEqualTo(2);
        assertThat(detail.get("reviewSummary").get("averageTenths").asInt()).isEqualTo(35);

        String reviewId = created.get("id").asString();
        assertThat(call(post("/api/v1/ops/reviews/" + reviewId + "/moderation").content("{\"visibility\":\"HIDDEN\",\"reason\":\"personal data\","
                + "\"expectedVersion\":0}"), cook).getStatus()).isEqualTo(403);
        assertThat(call(post("/api/v1/ops/reviews/" + reviewId + "/moderation").content("{\"visibility\":\"HIDDEN\",\"expectedVersion\":0}"), moderator)
                .getStatus()).as("reason required").isEqualTo(422);
        ok(call(post("/api/v1/ops/reviews/" + reviewId + "/moderation").content("{\"visibility\":\"HIDDEN\",\"reason\":\"personal data\",\"expectedVersion\":0}"),
                moderator), 200);
        JsonNode after = ok(call(get("/api/v1/kitchens/" + kitchen + "/reviews"), null), 200);
        assertThat(after.get("items").size()).isEqualTo(1);
        assertThat(after.get("summary").get("count").asInt()).as("aggregate excludes hidden").isEqualTo(1);
        assertThat(after.get("summary").get("averageTenths").asInt()).isEqualTo(20);
        assertThat(call(post("/api/v1/reviews/" + reviewId + "/reports").content("{\"reasonCode\":\"SPAM\"}"), other).getStatus())
                .as("hidden reviews cannot be reported").isEqualTo(404);
        JsonNode hiddenQueue = ok(call(get("/api/v1/ops/reviews").param("visibility", "HIDDEN"), moderator), 200);
        JsonNode hiddenEntry = entry(hiddenQueue, reviewId);
        assertThat(hiddenEntry).as("hidden review listed for restoring").isNotNull();
        assertThat(hiddenEntry.get("moderationReason").asString()).isEqualTo("personal data");
        assertThat(hiddenEntry.get("version").asInt()).isEqualTo(1);
        assertThat(call(get("/api/v1/ops/reviews"), cook).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/reviews"), other).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/reviews").param("visibility", "ALL"), moderator).getStatus()).isEqualTo(422);
        ok(call(post("/api/v1/ops/reviews/" + reviewId + "/moderation").content("{\"visibility\":\"VISIBLE\",\"reason\":\"edited by support\",\"expectedVersion\":1}"),
                moderator), 200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'review.moderation' AND object_id = ?", Integer.class, reviewId))
                .isEqualTo(2);

        assertThat(call(post("/api/v1/reviews/" + reviewId + "/reports").content("{\"reasonCode\":\"RUDE\"}"), other).getStatus()).isEqualTo(422);
        assertThat(call(post("/api/v1/reviews/" + reviewId + "/reports").content("{\"reasonCode\":\"OFFENSIVE\"}"), other).getStatus()).isEqualTo(204);
        assertThat(call(post("/api/v1/reviews/" + reviewId + "/reports").content("{\"reasonCode\":\"OFFENSIVE\"}"), other).getStatus()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM review_report WHERE review_id = ?::uuid", Integer.class, reviewId)).as("once per reporter")
                .isEqualTo(1);
        JsonNode reportedQueue = ok(call(get("/api/v1/ops/reviews"), moderator), 200);
        JsonNode reported = entry(reportedQueue, reviewId);
        assertThat(reported).as("reported visible review queued").isNotNull();
        assertThat(reported.get("reportCount").asInt()).isEqualTo(1);
        assertThat(reported.get("reportReasons").toString()).isEqualTo("[\"OFFENSIVE\"]");
        assertThat(reported.get("version").asInt()).isEqualTo(2);
        String reporterId = jdbc.queryForObject("SELECT reporter_id::text FROM review_report WHERE review_id = ?::uuid", String.class, reviewId);
        assertThat(reportedQueue.toString()).as("no reporter identity").doesNotContain(reporterId).doesNotContain("reporter");
        String unreported = jdbc.queryForObject("SELECT id::text FROM review WHERE order_id = ?", String.class, otherDone);
        assertThat(entry(reportedQueue, unreported)).as("unreported reviews are not queued").isNull();
        assertThat(call(post("/api/v1/reviews/" + reviewId + "/reports").content("{\"reasonCode\":\"OFFENSIVE\"}"), null).getStatus()).isEqualTo(401);
    }

    private static JsonNode entry(JsonNode queue, String id) {
        for (JsonNode item : queue.get("items")) {
            if (item.get("id").asString().equals(id)) {
                return item;
            }
        }
        return null;
    }

    @Test
    void favorites_are_idempotent_annotate_unavailable_meals_and_orders_stay_private() throws Exception {
        ok(call(put("/api/v1/me/favorites/" + meal), customer), 200);
        ok(call(put("/api/v1/me/favorites/" + meal), customer), 200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM favorite WHERE meal_id = ?", Integer.class, meal)).as("one favorite").isEqualTo(1);
        JsonNode list = ok(call(get("/api/v1/me/favorites"), customer), 200);
        assertThat(list.get("items").get(0).get("available").asBoolean()).isTrue();
        assertThat(ok(call(get("/api/v1/me/favorites"), other), 200).get("items").size()).isZero();

        jdbc.update("UPDATE meal SET status = 'ARCHIVED', archived_at = now() WHERE id = ?", meal);
        JsonNode archived = ok(call(get("/api/v1/me/favorites"), customer), 200);
        assertThat(archived.get("items").size()).as("kept, annotated").isEqualTo(1);
        assertThat(archived.get("items").get(0).get("available").asBoolean()).isFalse();
        assertThat(call(put("/api/v1/me/favorites/" + meal), other).getStatus()).as("only public meals can be added").isEqualTo(404);
        assertThat(call(delete("/api/v1/me/favorites/" + meal), customer).getStatus()).isEqualTo(204);
        assertThat(call(delete("/api/v1/me/favorites/" + meal), customer).getStatus()).as("idempotent").isEqualTo(204);
        assertThat(call(get("/api/v1/me/favorites"), null).getStatus()).isEqualTo(401);

        jdbc.update("UPDATE meal SET status = 'PUBLISHED', archived_at = NULL WHERE id = ?", meal);
        UUID order = order(customer);
        assertThat(call(get("/api/v1/orders/" + order), other).getStatus()).as("a shared order link is private").isEqualTo(404);
        assertThat(call(get("/api/v1/orders/" + order), null).getStatus()).isEqualTo(401);
    }
}
