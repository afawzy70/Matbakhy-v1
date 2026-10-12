package com.matbakhy.app.support.cases;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
 * TC-041 and TC-124 on PostgreSQL: order-scoped cases, staff-only notes never shown to customers, safety escalation,
 * SLA, cancellation requests that never change the order themselves, resolution through an audited exception
 * cancellation, and the operations order queue with failure flags.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class SupportIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;

    TestCommerce fx;
    String customerSubject;
    String otherCustomerSubject;
    String cookSubject;
    String agentSubject;
    String opsSubject;
    UUID meal;
    UUID slot;
    UUID address;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
        fx.activatePolicy(0);
        customerSubject = UUID.randomUUID().toString();
        UUID customer = logins.login(customerSubject, "Customer").getPrincipal().id();
        fx.verifyPhone(customer);
        otherCustomerSubject = UUID.randomUUID().toString();
        logins.login(otherCustomerSubject, "Other");
        cookSubject = UUID.randomUUID().toString();
        UUID cook = logins.login(cookSubject, "Cook").getPrincipal().id();
        agentSubject = staff("support.case");
        opsSubject = staff("order.ops", "order.exception_cancel");
        UUID kitchen = TestKitchens.approvedKitchen(jdbc, cook, logins.login(staff("cook.review"), "Reviewer").getPrincipal().id());
        UUID zone = fx.zone(3000);
        fx.deliversTo(kitchen, zone);
        meal = fx.meal(kitchen, 10000, "محشي", "Mahshi");
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

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, String who) throws Exception {
        return mvc.perform(r.with(authentication(as(who))).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    private static String key() {
        return "sup-" + UUID.randomUUID();
    }

    private long version(UUID order) {
        return jdbc.queryForObject("SELECT version FROM customer_order WHERE id = ?", Long.class, order);
    }

    private UUID placeOrder() throws Exception {
        JsonNode quote = ok(call(post("/api/v1/quotes").content("{\"items\":[{\"mealId\":\"" + meal + "\",\"quantity\":1}],\"slotId\":\"" + slot
                + "\",\"fulfillmentMode\":\"DELIVERY\",\"addressId\":\"" + address + "\"}"), customerSubject), 201);
        JsonNode order = ok(call(post("/api/v1/orders").header("Idempotency-Key", key()).content("""
                {"quoteId":"%s","clientRequestId":"%s","acceptedPolicyVersions":{"terms":"%s","cancellation":"%s"}}"""
                .formatted(quote.get("quoteId").asString(), UUID.randomUUID(), TestCommerce.TERMS, TestCommerce.CANCELLATION)), customerSubject), 201);
        return UUID.fromString(order.get("id").asString());
    }

    private void cook(UUID order, String action) throws Exception {
        ok(call(post("/api/v1/cook/orders/" + order + "/" + action).header("Idempotency-Key", key()).content("{\"expectedVersion\":" + version(order) + "}"),
                cookSubject), 200);
    }

    private JsonNode openCase(UUID order, String category, String description) throws Exception {
        return ok(call(post("/api/v1/support/cases").content("{\"orderId\":\"" + order + "\",\"category\":\"" + category + "\",\"description\":\""
                + description + "\"}"), customerSubject), 201);
    }

    private long caseVersion(String caseId) {
        return jdbc.queryForObject("SELECT version FROM support_case WHERE id = ?::uuid", Long.class, caseId);
    }

    // ---- TC-041 ----

    @Test
    void staff_notes_stay_private_and_safety_cases_are_escalated_first() throws Exception {
        UUID order = placeOrder();
        assertThat(call(post("/api/v1/support/cases").content("{\"orderId\":\"" + order + "\",\"category\":\"QUALITY\",\"description\":\"Not my order at all\"}"),
                otherCustomerSubject).getStatus()).as("another customer's order").isEqualTo(404);
        assertThat(call(post("/api/v1/support/cases").content("{\"orderId\":\"" + order + "\",\"category\":\"CANCELLATION_REQUEST\",\"description\":\"cancel please now\"}"),
                customerSubject).getStatus()).isEqualTo(422);
        assertThat(call(post("/api/v1/support/cases").content("{\"orderId\":\"" + order + "\",\"category\":\"QUALITY\",\"description\":\"short\"}"),
                customerSubject).getStatus()).isEqualTo(422);
        JsonNode quality = openCase(order, "QUALITY", "The rice was undercooked today");
        JsonNode safety = openCase(order, "FOOD_SAFETY", "Felt unwell after eating the meal");
        assertThat(safety.toString()).doesNotContain("severity").doesNotContain("slaDueAt").doesNotContain("assignee");
        assertThat(safety.get("reference").asString()).matches("CS-[0-9A-F]{8}");

        JsonNode queue = ok(call(get("/api/v1/ops/cases").param("state", "ACTIVE"), agentSubject), 200);
        JsonNode first = null;
        for (JsonNode c : queue.get("items")) {
            if (c.get("orderId").asString().equals(order.toString())) {
                first = c;
                break;
            }
        }
        assertThat(first.get("id").asString()).as("critical safety case ahead of the quality case").isEqualTo(safety.get("id").asString());
        assertThat(first.get("severity").asString()).isEqualTo("CRITICAL");
        assertThat(first.get("escalatedAt").isNull()).isFalse();
        assertThat(call(get("/api/v1/ops/cases"), customerSubject).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/cases"), opsSubject).getStatus()).as("order.ops is not support.case").isEqualTo(403);

        String caseId = quality.get("id").asString();
        ok(call(post("/api/v1/ops/cases/" + caseId + "/messages").header("Idempotency-Key", key())
                .content("{\"body\":\"Kitchen has a history of this; check photos\",\"visibility\":\"STAFF_ONLY\"}"), agentSubject), 201);
        assertThat(ok(call(get("/api/v1/ops/cases/" + caseId), agentSubject), 200).get("firstResponseAt").isNull())
                .as("a private note is not a response").isTrue();
        ok(call(post("/api/v1/ops/cases/" + caseId + "/messages").header("Idempotency-Key", key())
                .content("{\"body\":\"Sorry about that, we are checking with the kitchen\",\"visibility\":\"PUBLIC\"}"), agentSubject), 201);
        ok(call(post("/api/v1/support/cases/" + caseId + "/messages").header("Idempotency-Key", key()).content("{\"body\":\"Thank you\"}"),
                customerSubject), 201);

        JsonNode customerView = ok(call(get("/api/v1/support/cases/" + caseId), customerSubject), 200);
        assertThat(customerView.get("messages").size()).isEqualTo(2);
        assertThat(customerView.toString()).doesNotContain("history of this").doesNotContain("STAFF_ONLY");
        JsonNode staffView = ok(call(get("/api/v1/ops/cases/" + caseId), agentSubject), 200);
        assertThat(staffView.get("messages").size()).isEqualTo(3);
        assertThat(staffView.get("firstResponseAt").isNull()).isFalse();
        assertThat(staffView.get("state").asString()).isEqualTo("IN_PROGRESS");
        assertThat(call(get("/api/v1/support/cases/" + caseId), otherCustomerSubject).getStatus()).isEqualTo(404);
        assertThat(call(get("/api/v1/support/cases/" + caseId), cookSubject).getStatus()).as("cook has no case access").isEqualTo(404);
        assertThat(ok(call(get("/api/v1/support/cases"), customerSubject), 200).get("items").size()).isEqualTo(2);
        assertThat(ok(call(get("/api/v1/support/cases"), otherCustomerSubject), 200).get("items").size()).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE case_message SET body = 'edited' WHERE case_id = ?::uuid", caseId)).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO case_message (id, case_id, author_id, author_role, visibility, body, created_at) "
                + "SELECT gen_random_uuid(), id, customer_id, 'CUSTOMER', 'STAFF_ONLY', 'x', now() FROM support_case WHERE id = ?::uuid", caseId))
                .as("customers never write private notes").hasMessageContaining("case_message_customer_public_ck");
    }

    @Test
    void resolution_needs_a_code_and_reason_and_closed_cases_accept_no_messages_and_overdue_cases_are_flagged() throws Exception {
        UUID order = placeOrder();
        String caseId = openCase(order, "LATE", "The delivery is very late").get("id").asString();
        jdbc.update("UPDATE support_case SET sla_due_at = now() - interval '1 minute' WHERE id = ?::uuid", caseId);
        JsonNode overdue = ok(call(get("/api/v1/ops/cases").param("overdue", "true"), agentSubject), 200);
        assertThat(overdue.toString()).contains(caseId);
        assertThat(ok(call(get("/api/v1/ops/cases/" + caseId), agentSubject), 200).get("overdue").asBoolean()).isTrue();

        long v = caseVersion(caseId);
        assertThat(call(patch("/api/v1/ops/cases/" + caseId).content("{\"state\":\"RESOLVED\",\"reason\":\"done\",\"expectedVersion\":" + v + "}"),
                agentSubject).getStatus()).as("resolution code required").isEqualTo(422);
        assertThat(call(patch("/api/v1/ops/cases/" + caseId).content("{\"state\":\"RESOLVED\",\"resolutionCode\":\"INFORMATION_PROVIDED\",\"expectedVersion\":"
                + v + "}"), agentSubject).getStatus()).as("reason required").isEqualTo(422);
        assertThat(call(patch("/api/v1/ops/cases/" + caseId).content("{\"state\":\"CLOSED\",\"expectedVersion\":" + v + "}"), agentSubject).getStatus())
                .isEqualTo(409);
        assertThat(call(patch("/api/v1/ops/cases/" + caseId).content("{\"assignToMe\":true,\"expectedVersion\":" + (v + 5) + "}"), agentSubject).getStatus())
                .isEqualTo(409);
        JsonNode resolved = ok(call(patch("/api/v1/ops/cases/" + caseId).content("{\"assignToMe\":true,\"state\":\"RESOLVED\",\"resolutionCode\":"
                + "\"INFORMATION_PROVIDED\",\"reason\":\"courier delayed, customer informed\",\"expectedVersion\":" + v + "}"), agentSubject), 200);
        assertThat(resolved.get("assigneeId").isNull()).isFalse();
        assertThat(ok(call(get("/api/v1/ops/cases").param("mine", "true"), agentSubject), 200).toString()).contains(caseId);
        JsonNode closed = ok(call(patch("/api/v1/ops/cases/" + caseId).content("{\"state\":\"CLOSED\",\"expectedVersion\":"
                + resolved.get("version").asLong() + "}"), agentSubject), 200);
        assertThat(closed.get("state").asString()).isEqualTo("CLOSED");
        assertThat(call(post("/api/v1/support/cases/" + caseId + "/messages").header("Idempotency-Key", key()).content("{\"body\":\"hello?\"}"),
                customerSubject).getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'case.update' AND object_id = ?", Integer.class, caseId))
                .isEqualTo(2);
    }

    // ---- TC-124: cancellation request and exception resolution ----

    @Test
    void a_cancellation_request_never_changes_the_order_and_is_resolved_by_an_audited_exception_cancel() throws Exception {
        UUID order = placeOrder();
        assertThat(call(post("/api/v1/orders/" + order + "/cancellation-requests").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + version(order) + ",\"reasonCode\":\"CHANGED_MIND\"}"), customerSubject).getStatus())
                .as("pending orders are cancelled directly").isEqualTo(409);
        cook(order, "accept");
        long before = version(order);
        assertThat(call(post("/api/v1/orders/" + order + "/cancellation-requests").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + before + ",\"reasonCode\":\"CHANGED_MIND\"}"), otherCustomerSubject).getStatus()).isEqualTo(404);
        assertThat(call(post("/api/v1/orders/" + order + "/cancellation-requests").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + before + ",\"reasonCode\":\"BORED\"}"), customerSubject).getStatus()).isEqualTo(422);
        JsonNode request = ok(call(post("/api/v1/orders/" + order + "/cancellation-requests").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + before + ",\"reasonCode\":\"CHANGED_MIND\",\"note\":\"Plans changed, sorry\"}"), customerSubject), 201);
        assertThat(request.get("category").asString()).isEqualTo("CANCELLATION_REQUEST");
        assertThat(call(post("/api/v1/orders/" + order + "/cancellation-requests").header("Idempotency-Key", key())
                .content("{\"expectedVersion\":" + before + ",\"reasonCode\":\"OTHER\"}"), customerSubject).getStatus()).as("one open request").isEqualTo(409);
        assertThat(version(order)).as("order unchanged by the request").isEqualTo(before);
        JsonNode customerOrder = ok(call(get("/api/v1/orders/" + order), customerSubject), 200);
        assertThat(customerOrder.get("state").asString()).isEqualTo("ACCEPTED");
        assertThat(customerOrder.get("allowedActions").toString()).contains("REQUEST_CANCELLATION").doesNotContain("\"CANCEL\"");
        assertThat(customerOrder.get("supportCaseIds").toString()).contains(request.get("id").asString());

        String caseId = request.get("id").asString();
        JsonNode staffCase = ok(call(get("/api/v1/ops/cases/" + caseId), agentSubject), 200);
        // MB-039: a staff view of a case (customer description and conversation) leaves one audit row.
        assertThat(jdbc.queryForList("SELECT action || '|' || outcome FROM audit_event WHERE object_type = 'support_case' AND object_id = ? AND action LIKE 'access.%'",
                String.class, caseId)).containsExactly("access.support_case|ALLOWED");
        assertThat(staffCase.get("severity").asString()).isEqualTo("HIGH");
        assertThat(staffCase.get("cancellationReason").asString()).isEqualTo("CHANGED_MIND");
        assertThat(call(patch("/api/v1/ops/cases/" + caseId).content("{\"state\":\"RESOLVED\",\"resolutionCode\":\"ORDER_CANCELLED\",\"reason\":\"cancelled\","
                + "\"expectedVersion\":" + caseVersion(caseId) + "}"), agentSubject).getStatus()).as("order not cancelled yet").isEqualTo(409);

        // Operations queue: the order is flagged with an open case; contact is visible to order.ops while active.
        JsonNode flagged = ok(call(get("/api/v1/ops/orders").param("exception", "OPEN_CASE"), opsSubject), 200);
        assertThat(flagged.toString()).contains(order.toString());
        JsonNode opsView = ok(call(get("/api/v1/ops/orders/" + order), opsSubject), 200);
        assertThat(opsView.get("exceptions").toString()).contains("OPEN_CASE");
        assertThat(opsView.get("caseIds").toString()).contains(caseId);
        assertThat(opsView.get("customerContact").get("phone").asString()).startsWith("+20");
        // MB-039: a staff view that discloses customer contact leaves one audit row (no contact data in it).
        assertThat(jdbc.queryForList("SELECT action || '|' || outcome || '|' || coalesce(details::text, '') FROM audit_event WHERE object_type = 'order' AND object_id = ?",
                String.class, order.toString())).containsExactly("access.order_contact|ALLOWED|{}");
        assertThat(opsView.get("order").get("handoverCode").isNull()).isTrue();
        assertThat(call(get("/api/v1/ops/orders"), agentSubject).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/orders").param("exception", "WHATEVER"), opsSubject).getStatus()).isEqualTo(422);

        cook(order, "prepare");
        ok(call(post("/api/v1/ops/orders/" + order + "/exception-cancel").header("Idempotency-Key", key()).content("{\"expectedVersion\":" + version(order)
                + ",\"reasonCode\":\"OPERATIONAL\",\"caseId\":\"" + request.get("reference").asString() + "\",\"note\":\"customer request accepted\"}"),
                opsSubject), 200);
        JsonNode resolved = ok(call(patch("/api/v1/ops/cases/" + caseId).content("{\"state\":\"RESOLVED\",\"resolutionCode\":\"ORDER_CANCELLED\","
                + "\"reason\":\"exception cancellation done\",\"expectedVersion\":" + caseVersion(caseId) + "}"), agentSubject), 200);
        assertThat(resolved.get("state").asString()).isEqualTo("RESOLVED");
        JsonNode events = ok(call(get("/api/v1/orders/" + order + "/events"), customerSubject), 200);
        assertThat(events.toString()).contains("CANCELLED");
        assertThatThrownBy(() -> jdbc.update("UPDATE order_event SET to_state = 'COMPLETED' WHERE order_id = ?", order))
                .as("immutable order timeline").hasMessageContaining("append-only");
        assertThat(ok(call(get("/api/v1/support/cases/" + caseId), customerSubject), 200).get("state").asString()).isEqualTo("RESOLVED");
    }
}
