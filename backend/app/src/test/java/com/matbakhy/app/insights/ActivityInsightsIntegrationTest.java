package com.matbakhy.app.insights;

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
import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
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
 * MB-054 / TC-164 (owner decision D17, Stage 2): organic activity is recorded per visitor and day without storing who the
 * visitor is, so refreshes and repeated events count once; declared bots are ignored; counts stay per kitchen. The free
 * cook report takes orders, capacity and money from the server's own records, keeps gross sales, refunds and the known
 * net apart, and never claims profit. Nothing sponsored or billable exists.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class ActivityInsightsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;

    TestCommerce fx;
    String customerSubject;
    String cookSubject;
    String otherCookSubject;
    UUID kitchen;
    UUID otherKitchen;
    UUID meal;
    UUID otherMeal;
    UUID slot;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activateLaunchPolicy(0);
        customerSubject = UUID.randomUUID().toString();
        fx.verifyPhone(logins.login(customerSubject, "Customer").getPrincipal().id());
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        cookSubject = UUID.randomUUID().toString();
        kitchen = TestKitchens.approvedKitchen(jdbc, logins.login(cookSubject, "Cook").getPrincipal().id(), reviewer);
        otherCookSubject = UUID.randomUUID().toString();
        otherKitchen = TestKitchens.approvedKitchen(jdbc, logins.login(otherCookSubject, "Other cook").getPrincipal().id(), reviewer);
        meal = fx.meal(kitchen, 10000, "فتة", "Fatta");
        otherMeal = fx.meal(otherKitchen, 9000, "محشي", "Mahshi");
        slot = fx.slot(kitchen, 300, 20, meal, 10);
    }

    @AfterEach
    void restore() {
        fx.activatePolicy(0);
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        MockHttpServletRequestBuilder b = r.with(csrf()).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(who == null ? b : b.with(authentication(who))).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return r.getContentAsString().isEmpty() ? null : json.readTree(r.getContentAsString());
    }

    private MockHttpServletResponse activity(String visitor, String userAgent, String events) throws Exception {
        return call(post("/api/v1/activity").header("User-Agent", userAgent)
                .content("{\"visitorId\":\"" + visitor + "\",\"events\":[" + events + "]}"), null);
    }

    private static String event(String kind, UUID subject) {
        return "{\"kind\":\"" + kind + "\",\"subjectId\":\"" + subject + "\"}";
    }

    private UUID completedOrder() throws Exception {
        AccountAuthentication customer = logins.login(customerSubject, "Customer");
        UUID id = placed(1);
        for (String action : new String[] {"accept", "prepare", "ready"}) {
            cook(id, action, "");
        }
        cook(id, "handover", ",\"code\":\"" + ok(call(get("/api/v1/orders/" + id), customer), 200).get("handoverCode").asString() + "\"");
        return id;
    }

    private UUID placed(int quantity) throws Exception {
        AccountAuthentication customer = logins.login(customerSubject, "Customer");
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":" + quantity
                + "}],\"slotId\":\"" + slot + "\",\"fulfillmentMode\":\"PICKUP\"}"), customer), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", "ai-" + UUID.randomUUID()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), customer), 201);
        return UUID.fromString(order.get("id").asString());
    }

    private void cook(UUID order, String action, String extra) throws Exception {
        long version = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", "ai-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + version + extra + "}"), logins.login(cookSubject, "Cook")), 200);
    }

    private JsonNode insights(String subject, String role) throws Exception {
        // Today and tomorrow (Cairo): the test slot starts five hours ahead and may fall after midnight.
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("Africa/Cairo")));
        return ok(call(get("/api/v1/cook/insights").param("from", today.toString()).param("to", today.plusDays(1).toString()),
                logins.login(subject, role)), 200);
    }

    @Test
    void activity_is_deduplicated_per_visitor_and_day_bots_are_ignored_and_no_visitor_identity_is_stored() throws Exception {
        String browser = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0 Safari/537.36";
        String visitorA = "va-" + UUID.randomUUID();
        // Ten refreshes of the same pages by one visitor: one view, one impression, one add-to-cart.
        for (int i = 0; i < 10; i++) {
            ok(activity(visitorA, browser, event("MEAL_IMPRESSION", meal) + "," + event("MEAL_VIEW", meal) + ","
                    + event("KITCHEN_VIEW", kitchen) + "," + event("ADD_TO_CART", meal)), 202);
        }
        // A second visitor and a view of the other kitchen's meal.
        ok(activity("vb-" + UUID.randomUUID(), browser, event("MEAL_VIEW", meal) + "," + event("MEAL_VIEW", otherMeal)), 202);
        // A declared crawler is accepted (no signal) and not counted.
        ok(activity("bot-" + UUID.randomUUID(), "Mozilla/5.0 (compatible; Googlebot/2.1)", event("MEAL_VIEW", meal)), 202);
        // A signed-out phone sends no cookie and no CSRF token (MB-054 on native): still accepted and counted.
        assertThat(mvc.perform(post("/api/v1/activity").header("User-Agent", "okhttp/4.12.0").contentType(MediaType.APPLICATION_JSON)
                .content("{\"visitorId\":\"app-" + UUID.randomUUID() + "\",\"events\":[" + event("MEAL_VIEW", meal) + "]}"))
                .andReturn().getResponse().getStatus()).isEqualTo(202);
        // Unknown subjects are ignored without saying so; a malformed request is refused.
        ok(activity(visitorA, browser, event("MEAL_VIEW", UUID.randomUUID())), 202);
        assertThat(activity(visitorA, browser, "{\"kind\":\"PURCHASE\",\"subjectId\":\"" + meal + "\"}").getStatus()).isIn(400, 422);
        assertThat(activity("x", browser, event("MEAL_VIEW", meal)).getStatus()).as("visitor id too short").isIn(400, 422);

        JsonNode report = insights(cookSubject, "Cook");
        JsonNode a = report.get("activity");
        assertThat(a.get("mealViews").asInt()).as("two browsers and one phone").isEqualTo(3);
        assertThat(a.get("mealImpressions").asInt()).isEqualTo(1);
        assertThat(a.get("kitchenViews").asInt()).isEqualTo(1);
        assertThat(a.get("addToCart").asInt()).isEqualTo(1);
        JsonNode other = insights(otherCookSubject, "Other cook");
        assertThat(other.get("kitchenId").asString()).isEqualTo(otherKitchen.toString());
        assertThat(other.get("activity").get("mealViews").asInt()).as("per kitchen").isEqualTo(1);

        // Stored: a keyed per-day hash, never the visitor id, an address or an account.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_daily WHERE kitchen_id = ?", Integer.class, kitchen)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT length(visitor_hash)) || '|' || min(length(visitor_hash)) FROM activity_daily",
                String.class)).isEqualTo("1|32");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM activity_daily WHERE position(convert_to(?, 'UTF8') IN visitor_hash) > 0",
                Integer.class, visitorA)).isZero();
        assertThat(jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_name = 'activity_daily'", String.class))
                .containsExactlyInAnyOrder("day", "kind", "subject_id", "kitchen_id", "visitor_hash");
    }

    @Test
    void the_cook_report_keeps_gross_refunds_and_known_net_apart_from_server_records_and_claims_no_profit() throws Exception {
        UUID first = completedOrder();
        completedOrder();
        UUID cancelled = placed(1);
        long v = jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, cancelled);
        ok(call(post("/api/v1/orders/" + cancelled + "/cancel").header("Idempotency-Key", "ai-" + UUID.randomUUID())
                .content("{\"expectedVersion\":" + v + ",\"reasonCode\":\"CHANGED_MIND\"}"), logins.login(customerSubject, "Customer")), 200);
        // A refund paid out for the first order (test fixture; the refund workflow itself is covered by MB-025 tests).
        UUID staff = logins.login(UUID.randomUUID().toString(), "Staff").getPrincipal().id();
        UUID payer = logins.login(UUID.randomUUID().toString(), "Payer").getPrincipal().id();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO refund (id, order_id, kitchen_id, case_reference, amount_minor, state, reason, requested_by, approved_by, paid_by,
                                    payment_reference, created_at, updated_at)
                VALUES (?, ?, ?, 'CASE-1', 2500, 'PAID', 'synthetic partial refund', ?, ?, ?, 'REF-1', ?, ?)""",
                UUID.randomUUID(), first, kitchen, staff, payer, payer, now, now);

        JsonNode report = insights(cookSubject, "Cook");
        JsonNode o = report.get("orders");
        assertThat(o.get("quotes").asInt()).isEqualTo(3);
        assertThat(o.get("placed").asInt()).isEqualTo(3);
        assertThat(o.get("completed").asInt()).isEqualTo(2);
        assertThat(o.get("cancelled").asInt()).isEqualTo(1);
        assertThat(o.get("repeatCustomers").asInt()).as("one customer with two completed orders").isEqualTo(1);
        JsonNode m = report.get("money");
        assertThat(m.get("grossSales").get("amountMinor").asLong()).isEqualTo(20000);
        assertThat(m.get("refunds").get("amountMinor").asLong()).isEqualTo(2500);
        assertThat(m.get("netKnown").get("amountMinor").asLong()).isEqualTo(17500);
        JsonNode c = report.get("capacity");
        assertThat(c.get("portionsOffered").asInt()).isEqualTo(20);
        assertThat(c.get("portionsTaken").asInt()).as("two completed portions; the cancelled one was released").isEqualTo(2);
        assertThat(report.get("timezone").asString()).isEqualTo("Africa/Cairo");
        assertThat(report.get("definitions").toString()).contains("NET_IS_NOT_PROFIT");

        String body = report.toString().toLowerCase();
        assertThat(body).doesNotContain("profit\":", "margin", "sponsor", "customerid", "phone", customerSubject);
        assertThat(insights(otherCookSubject, "Other cook").get("money").get("grossSales").get("amountMinor").asLong()).isZero();
    }

    @Test
    void only_a_cook_sees_insights_and_only_for_the_own_kitchen() throws Exception {
        assertThat(call(get("/api/v1/cook/insights"), logins.login(customerSubject, "Customer")).getStatus()).isEqualTo(403);
        assertThat(mvc.perform(get("/api/v1/cook/insights")).andReturn().getResponse().getStatus()).isEqualTo(401);
        JsonNode own = ok(call(get("/api/v1/cook/insights").param("kitchenId", otherKitchen.toString()), logins.login(cookSubject, "Cook")), 200);
        assertThat(own.get("kitchenId").asString()).as("no parameter can select another kitchen").isEqualTo(kitchen.toString());
        assertThat(call(get("/api/v1/cook/insights").param("from", "2026-01-01").param("to", "2027-06-01"), logins.login(cookSubject, "Cook"))
                .getStatus()).isEqualTo(422);
    }

    @Test
    void discovery_has_no_sponsored_results() throws Exception {
        String body = mvc.perform(get("/api/v1/meals")).andReturn().getResponse().getContentAsString().toLowerCase();
        assertThat(body).doesNotContain("sponsor", "promoted", "advert");
    }
}
