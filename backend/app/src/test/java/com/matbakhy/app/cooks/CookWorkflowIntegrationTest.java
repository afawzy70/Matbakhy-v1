package com.matbakhy.app.cooks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.cooks.api.KitchenDirectory.KitchenStatus;
import com.matbakhy.cooks.api.KitchenDirectory.OperatingState;
import java.util.ArrayList;
import java.util.List;
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
 * TC-008, TC-009, TC-112 on PostgreSQL through the real security chain: cook application drafts, complete
 * submission, scoped and step-up reviewer decisions with reasons, immutable revision history, kitchen
 * creation on approval, separate operating state and the public profile without private data.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class CookWorkflowIntegrationTest {

    static final String PHONE_LOCAL = "0100 123 4567";
    static final String PHONE_E164 = "+201001234567";
    static final String LOCATION = "Synthetic street 12, flat 3 (test only)";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired KitchenDirectory kitchens;

    String applicantSubject;
    String reviewerSubject;
    String otherSubject;
    UUID zone;
    UUID inactiveZone;

    @BeforeEach
    void setUp() {
        applicantSubject = UUID.randomUUID().toString();
        reviewerSubject = UUID.randomUUID().toString();
        otherSubject = UUID.randomUUID().toString();
        zone = zone(true);
        inactiveZone = zone(false);
        grant(reviewerSubject, "cook.review");
    }

    // ---- helpers ----

    private UUID zone(boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                + "VALUES (?, ?, 'منطقة اختبار', 'Test zone', 'مدينة', 'City', ?, 3000)", id, "z-" + id.toString().substring(0, 8), active);
        return id;
    }

    private void grant(String subject, String permission) {
        UUID user = logins.login(subject, "Staff").getPrincipal().id();
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, ?, 'test grant')",
                UUID.randomUUID(), user, permission);
    }

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "User");
    }

    private AccountAuthentication mfa(String subject) {
        return logins.loginWithMfa(subject, "User");
    }

    private UUID id(String subject) {
        return as(subject).getPrincipal().id();
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        if (who != null) {
            r = r.with(authentication(who));
        }
        return mvc.perform(r.with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private String completeDraft(long expectedVersion) {
        return """
                {"publicName":"Synthetic Kitchen","bio":"Home-style test meals","contactPhone":"%s","privateLocation":"%s",
                 "pickupEnabled":true,"zoneIds":["%s"],"expectedVersion":%d}""".formatted(PHONE_LOCAL, LOCATION, zone, expectedVersion);
    }

    private JsonNode saveDraft(String subject, String draft) throws Exception {
        MockHttpServletResponse r = call(put("/api/v1/cook/application").content(draft), as(subject));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private JsonNode submit(String subject, long version) throws Exception {
        MockHttpServletResponse r = call(post("/api/v1/cook/application/submit").content("{\"expectedVersion\":" + version + "}"),
                as(subject));
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private MockHttpServletResponse decide(AccountAuthentication who, UUID application, String decision, String reason, int revision,
                                           long version, String key) throws Exception {
        String reasonJson = reason == null ? "null" : "\"" + reason + "\"";
        MockHttpServletRequestBuilder r = post("/api/v1/ops/cooks/" + application + "/decision").content(
                "{\"decision\":\"%s\",\"reason\":%s,\"revision\":%d,\"expectedVersion\":%d}".formatted(decision, reasonJson, revision, version));
        if (key != null) {
            r = r.header("Idempotency-Key", key);
        }
        return call(r, who);
    }

    private JsonNode decideOk(UUID application, String decision, String reason, int revision, long version) throws Exception {
        MockHttpServletResponse r = decide(mfa(reviewerSubject), application, decision, reason, revision, version, key());
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        return body(r);
    }

    private static String key() {
        return "key-" + UUID.randomUUID();
    }

    /** Submitted application under review; returns its id. */
    private UUID underReview(String subject) throws Exception {
        long v = saveDraft(subject, completeDraft(0)).get("version").asLong();
        JsonNode submitted = submit(subject, v);
        UUID app = UUID.fromString(submitted.get("id").asString());
        decideOk(app, "START_REVIEW", null, 1, submitted.get("version").asLong());
        return app;
    }

    private long version(UUID application) {
        return jdbc.queryForObject("SELECT version FROM cook_application WHERE id = ?", Long.class, application);
    }

    private UUID approvedKitchen(String subject) throws Exception {
        UUID app = underReview(subject);
        JsonNode approved = decideOk(app, "APPROVE", "Documents and interview complete", 1, version(app));
        return UUID.fromString(approved.get("kitchenId").asString());
    }

    private MockHttpServletResponse operatingState(AccountAuthentication who, UUID kitchen, String action, String reason)
            throws Exception {
        long v = jdbc.queryForObject("SELECT version FROM kitchen WHERE id = ?", Long.class, kitchen);
        return call(post("/api/v1/ops/kitchens/" + kitchen + "/operating-state").content(
                "{\"action\":\"%s\",\"reason\":\"%s\",\"expectedVersion\":%d}".formatted(action, reason, v)), who);
    }

    private int audits(UUID actor, String action, String outcome) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND action = ? AND outcome = ?",
                Integer.class, actor, action, outcome);
    }

    // ---- applicant drafts and submission ----

    @Test
    void applicant_saves_a_private_draft_with_normalized_phone_and_no_cook_status() throws Exception {
        assertThat(call(get("/api/v1/cook/application"), as(applicantSubject)).getStatus()).isEqualTo(404);
        JsonNode draft = saveDraft(applicantSubject, "{\"publicName\":\"  Synthetic Kitchen \",\"contactPhone\":\"" + PHONE_LOCAL + "\"}");
        assertThat(draft.get("state").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("draft").get("publicName").asString()).isEqualTo("Synthetic Kitchen");
        assertThat(draft.get("draft").get("contactPhone").asString()).isEqualTo(PHONE_E164);
        assertThat(draft.get("currentRevision").asInt()).isZero();

        JsonNode again = body(call(get("/api/v1/cook/application"), as(applicantSubject)));
        assertThat(again.get("id")).isEqualTo(draft.get("id"));
        // Another account sees only its own (absent) application; there is no cook status from applying.
        assertThat(call(get("/api/v1/cook/application"), as(otherSubject)).getStatus()).isEqualTo(404);
        assertThat(kitchens.ownedBy(id(applicantSubject))).isEmpty();
        assertThat(call(get("/api/v1/ops/cooks/" + draft.get("id").asString()), as(otherSubject)).getStatus()).isEqualTo(403);
    }

    @Test
    void stale_version_and_invalid_fields_are_rejected() throws Exception {
        long v = saveDraft(applicantSubject, "{\"publicName\":\"Synthetic Kitchen\"}").get("version").asLong();
        MockHttpServletResponse stale = call(put("/api/v1/cook/application").content("{\"publicName\":\"Other\",\"expectedVersion\":"
                + (v + 5) + "}"), as(applicantSubject));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("VERSION_CONFLICT");

        MockHttpServletResponse invalid = call(put("/api/v1/cook/application").content(
                "{\"contactPhone\":\"+12025550100\",\"zoneIds\":[\"%s\"],\"expectedVersion\":%d}".formatted(inactiveZone, v)),
                as(applicantSubject));
        assertThat(invalid.getStatus()).isEqualTo(422);
        assertThat(invalid.getContentAsString()).contains("INVALID_EGYPTIAN_MOBILE").contains("ZONE_NOT_SERVICEABLE");

        MockHttpServletResponse unknownField = call(put("/api/v1/cook/application").content(
                "{\"publicName\":\"X Kitchen\",\"state\":\"APPROVED\",\"expectedVersion\":" + v + "}"), as(applicantSubject));
        assertThat(unknownField.getStatus()).as("client cannot set state").isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT state FROM cook_application WHERE user_id = ?", String.class, id(applicantSubject)))
                .isEqualTo("DRAFT");
    }

    @Test
    void incomplete_application_cannot_be_submitted_and_creates_no_revision() throws Exception {
        long v = saveDraft(applicantSubject, "{\"publicName\":\"Synthetic Kitchen\",\"pickupEnabled\":false}").get("version").asLong();
        MockHttpServletResponse r = call(post("/api/v1/cook/application/submit").content("{\"expectedVersion\":" + v + "}"),
                as(applicantSubject));
        assertThat(r.getStatus()).isEqualTo(422);
        JsonNode errors = body(r).get("fieldErrors");
        List<String> fields = new ArrayList<>();
        errors.forEach(e -> fields.add(e.get("field").asString() + ":" + e.get("code").asString()));
        assertThat(fields).containsExactlyInAnyOrder("bio:REQUIRED", "contactPhone:REQUIRED", "privateLocation:REQUIRED",
                "zoneIds:PICKUP_OR_ZONE_REQUIRED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM cook_application_revision r JOIN cook_application a ON a.id = r.application_id "
                + "WHERE a.user_id = ?", Integer.class, id(applicantSubject))).isZero();
        assertThat(kitchens.ownedBy(id(applicantSubject))).isEmpty();
    }

    @Test
    void zone_deactivated_after_draft_blocks_submission() throws Exception {
        long v = saveDraft(applicantSubject, completeDraft(0).replace("\"pickupEnabled\":true", "\"pickupEnabled\":false"))
                .get("version").asLong();
        jdbc.update("UPDATE service_zone SET active = false WHERE id = ?", zone);
        MockHttpServletResponse r = call(post("/api/v1/cook/application/submit").content("{\"expectedVersion\":" + v + "}"),
                as(applicantSubject));
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).contains("ZONE_NOT_SERVICEABLE");
    }

    // ---- TC-008 / TC-112: scoped review, history, approval creates the kitchen ----

    @Test
    void changes_requested_then_resubmitted_then_approved_preserves_every_revision() throws Exception {
        UUID app = underReview(applicantSubject);
        JsonNode changes = decideOk(app, "REQUEST_CHANGES", "Please describe your kitchen hygiene routine", 1, version(app));
        assertThat(changes.get("state").asString()).isEqualTo("CHANGES_REQUESTED");
        assertThat(changes.get("kitchenId").isNull()).isTrue();
        assertThat(kitchens.ownedBy(id(applicantSubject))).as("no selling before approval").isEmpty();

        JsonNode mine = body(call(get("/api/v1/cook/application"), as(applicantSubject)));
        assertThat(mine.get("history").get(0).get("decisionReason").asString()).contains("hygiene");
        assertThat(mine.get("history").get(0).has("reviewerId")).as("reviewer identity stays internal").isFalse();
        JsonNode edited = saveDraft(applicantSubject, completeDraft(mine.get("version").asLong())
                .replace("Home-style test meals", "Home-style test meals; surfaces cleaned twice daily"));
        JsonNode resubmitted = submit(applicantSubject, edited.get("version").asLong());
        assertThat(resubmitted.get("state").asString()).isEqualTo("SUBMITTED");
        assertThat(resubmitted.get("currentRevision").asInt()).isEqualTo(2);

        // A decision aimed at the old revision is refused.
        MockHttpServletResponse stale = decide(mfa(reviewerSubject), app, "START_REVIEW", null, 1, version(app), key());
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("STATE_CONFLICT");

        decideOk(app, "START_REVIEW", null, 2, version(app));
        JsonNode approved = decideOk(app, "APPROVE", "Operational checks complete", 2, version(app));
        assertThat(approved.get("state").asString()).isEqualTo("APPROVED");
        JsonNode revisions = approved.get("revisions");
        assertThat(revisions.size()).isEqualTo(2);
        assertThat(revisions.get(0).get("decision").asString()).isEqualTo("CHANGES_REQUESTED");
        assertThat(revisions.get(0).get("bio").asString()).isEqualTo("Home-style test meals");
        assertThat(revisions.get(1).get("decision").asString()).isEqualTo("APPROVED");
        assertThat(revisions.get(1).get("reviewerId").asString()).isEqualTo(id(reviewerSubject).toString());

        UUID kitchenId = UUID.fromString(approved.get("kitchenId").asString());
        KitchenStatus status = kitchens.ownedBy(id(applicantSubject)).orElseThrow();
        assertThat(status.kitchenId()).isEqualTo(kitchenId);
        assertThat(status.canSell()).isTrue();
        assertThat(jdbc.queryForObject("SELECT approved_revision FROM kitchen WHERE id = ?", Integer.class, kitchenId)).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT zone_id FROM kitchen_zone WHERE kitchen_id = ?", UUID.class, kitchenId)).containsExactly(zone);

        // Approved applications are no longer editable by the applicant.
        MockHttpServletResponse locked = call(put("/api/v1/cook/application").content(completeDraft(version(app))), as(applicantSubject));
        assertThat(locked.getStatus()).isEqualTo(409);
    }

    @Test
    void rejection_preserves_history_and_never_creates_a_kitchen() throws Exception {
        UUID app = underReview(applicantSubject);
        JsonNode rejected = decideOk(app, "REJECT", "Kitchen does not meet operational requirements", 1, version(app));
        assertThat(rejected.get("state").asString()).isEqualTo("REJECTED");
        assertThat(rejected.get("revisions").get(0).get("decisionReason").asString()).contains("operational requirements");
        assertThat(kitchens.ownedBy(id(applicantSubject))).isEmpty();

        assertThat(call(put("/api/v1/cook/application").content(completeDraft(version(app))), as(applicantSubject)).getStatus())
                .isEqualTo(409);
        assertThat(call(post("/api/v1/cook/application/submit").content("{\"expectedVersion\":" + version(app) + "}"),
                as(applicantSubject)).getStatus()).isEqualTo(409);
        assertThat(decide(mfa(reviewerSubject), app, "APPROVE", "changed my mind", 1, version(app), key()).getStatus())
                .as("a decided revision cannot be re-decided").isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT decision FROM cook_application_revision WHERE application_id = ?", String.class, app))
                .isEqualTo("REJECTED");
    }

    @Test
    void only_a_scoped_reviewer_with_recent_mfa_can_decide() throws Exception {
        UUID app = underReview(applicantSubject);
        long v = version(app);
        String financeSubject = UUID.randomUUID().toString();
        grant(financeSubject, "finance.read");

        assertThat(decide(mfa(otherSubject), app, "APPROVE", "looks fine to me", 1, v, key()).getStatus()).isEqualTo(403);
        assertThat(decide(mfa(financeSubject), app, "APPROVE", "looks fine to me", 1, v, key()).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/cooks"), as(financeSubject)).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/ops/cooks/" + app), as(otherSubject)).getStatus()).isEqualTo(403);
        MockHttpServletResponse noMfa = decide(as(reviewerSubject), app, "APPROVE", "looks fine to me", 1, v, key());
        assertThat(noMfa.getStatus()).isEqualTo(403);
        assertThat(noMfa.getContentAsString()).contains("STEP_UP_REQUIRED");
        assertThat(call(post("/api/v1/ops/cooks/" + app + "/decision").content("{}"), null).getStatus()).isEqualTo(401);

        assertThat(version(app)).as("no denied attempt changed the application").isEqualTo(v);
        assertThat(jdbc.queryForObject("SELECT state FROM cook_application WHERE id = ?", String.class, app)).isEqualTo("UNDER_REVIEW");
        assertThat(kitchens.ownedBy(id(applicantSubject))).isEmpty();
    }

    @Test
    void a_reviewer_cannot_approve_their_own_application_and_the_attempt_is_audited() throws Exception {
        long v = saveDraft(reviewerSubject, completeDraft(0)).get("version").asLong();
        JsonNode submitted = submit(reviewerSubject, v);
        UUID app = UUID.fromString(submitted.get("id").asString());
        UUID reviewer = id(reviewerSubject);

        MockHttpServletResponse self = decide(mfa(reviewerSubject), app, "START_REVIEW", null, 1, submitted.get("version").asLong(), key());
        assertThat(self.getStatus()).isEqualTo(403);
        assertThat(audits(reviewer, "cook.application.decision", "DENIED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT state FROM cook_application WHERE id = ?", String.class, app)).isEqualTo("SUBMITTED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_record WHERE actor_id = ?", Integer.class, reviewer)).isZero();
    }

    @Test
    void decisions_need_reason_review_state_and_current_version() throws Exception {
        long v = saveDraft(applicantSubject, completeDraft(0)).get("version").asLong();
        JsonNode submitted = submit(applicantSubject, v);
        UUID app = UUID.fromString(submitted.get("id").asString());

        MockHttpServletResponse skip = decide(mfa(reviewerSubject), app, "APPROVE", "fast track", 1, version(app), key());
        assertThat(skip.getStatus()).as("approval only from UNDER_REVIEW").isEqualTo(409);
        decideOk(app, "START_REVIEW", null, 1, version(app));
        MockHttpServletResponse noReason = decide(mfa(reviewerSubject), app, "APPROVE", null, 1, version(app), key());
        assertThat(noReason.getStatus()).isEqualTo(422);
        assertThat(noReason.getContentAsString()).contains("\"field\":\"reason\"");
        MockHttpServletResponse staleVersion = decide(mfa(reviewerSubject), app, "APPROVE", "complete", 1, version(app) - 1, key());
        assertThat(staleVersion.getStatus()).isEqualTo(409);
        assertThat(staleVersion.getContentAsString()).contains("VERSION_CONFLICT");
        MockHttpServletResponse noKey = decide(mfa(reviewerSubject), app, "APPROVE", "complete", 1, version(app), null);
        assertThat(noKey.getStatus()).isEqualTo(400);
        assertThat(kitchens.ownedBy(id(applicantSubject))).isEmpty();
        assertThat(jdbc.queryForObject("SELECT state FROM cook_application WHERE id = ?", String.class, app)).isEqualTo("UNDER_REVIEW");
    }

    @Test
    void repeated_decision_with_same_key_applies_once_and_key_reuse_with_other_body_conflicts() throws Exception {
        UUID app = underReview(applicantSubject);
        long v = version(app);
        String k = key();
        MockHttpServletResponse first = decide(mfa(reviewerSubject), app, "APPROVE", "Operational checks complete", 1, v, k);
        MockHttpServletResponse replay = decide(mfa(reviewerSubject), app, "APPROVE", "Operational checks complete", 1, v, k);
        assertThat(first.getStatus()).isEqualTo(200);
        assertThat(replay.getStatus()).isEqualTo(200);
        assertThat(body(replay)).isEqualTo(body(first));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kitchen WHERE application_id = ?", Integer.class, app)).isEqualTo(1);
        assertThat(audits(id(reviewerSubject), "cook.application.decision", "ALLOWED")).as("start review + one approval").isEqualTo(2);

        MockHttpServletResponse conflict = decide(mfa(reviewerSubject), app, "REJECT", "Operational checks complete", 1, v, k);
        assertThat(conflict.getStatus()).isEqualTo(409);
        assertThat(conflict.getContentAsString()).contains("IDEMPOTENCY_CONFLICT");
    }

    @Test
    void concurrent_approvals_create_exactly_one_kitchen() throws Exception {
        UUID app = underReview(applicantSubject);
        long v = version(app);
        String shared = key();
        AccountAuthentication reviewer = mfa(reviewerSubject);
        List<java.util.concurrent.Callable<Integer>> calls = List.of(
                () -> decide(reviewer, app, "APPROVE", "Operational checks complete", 1, v, shared).getStatus(),
                () -> decide(reviewer, app, "APPROVE", "Operational checks complete", 1, v, shared).getStatus(),
                () -> decide(reviewer, app, "APPROVE", "Operational checks complete", 1, v, key()).getStatus(),
                () -> decide(reviewer, app, "APPROVE", "Operational checks complete", 1, v, key()).getStatus());
        var pool = java.util.concurrent.Executors.newFixedThreadPool(calls.size());
        List<Integer> statuses = new ArrayList<>();
        try {
            for (var f : pool.invokeAll(calls)) {
                statuses.add(f.get());
            }
        } finally {
            pool.shutdown();
        }
        assertThat(statuses).allMatch(st -> st == 200 || st == 409);
        assertThat(statuses).contains(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM kitchen WHERE application_id = ?", Integer.class, app)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE object_id = ? AND action = 'cook.application.decision' "
                + "AND outcome = 'ALLOWED' AND details->>'decision' = 'APPROVE'", Integer.class, app.toString())).isEqualTo(1);
    }

    @Test
    void submitted_revisions_are_immutable_in_the_database() throws Exception {
        UUID app = underReview(applicantSubject);
        decideOk(app, "REJECT", "Not eligible in this pilot", 1, version(app));
        assertThatThrownBy(() -> jdbc.update("UPDATE cook_application_revision SET bio = 'rewritten' WHERE application_id = ?", app))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("UPDATE cook_application_revision SET decision_reason = 'softened' WHERE application_id = ?", app))
                .hasMessageContaining("immutable");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM cook_application_revision WHERE application_id = ?", app))
                .hasMessageContaining("append-only");
    }

    @Test
    void review_queue_excludes_drafts_pages_with_a_cursor_and_detail_access_is_audited() throws Exception {
        saveDraft(otherSubject, "{\"publicName\":\"Draft Only Kitchen\"}");
        List<UUID> submitted = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            String subject = UUID.randomUUID().toString();
            long v = saveDraft(subject, completeDraft(0)).get("version").asLong();
            submitted.add(UUID.fromString(submit(subject, v).get("id").asString()));
        }
        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletResponse page = call(get("/api/v1/ops/cooks").param("state", "SUBMITTED").param("limit", "2")
                    .param("cursor", cursor), as(reviewerSubject));
            assertThat(page.getStatus()).isEqualTo(200);
            assertThat(page.getContentAsString()).doesNotContain(LOCATION).doesNotContain(PHONE_E164);
            JsonNode node = body(page);
            node.get("items").forEach(i -> seen.add(i.get("id").asString()));
            cursor = node.get("nextCursor").isNull() ? null : node.get("nextCursor").asString();
        } while (cursor != null);
        assertThat(seen).containsAll(submitted.stream().map(UUID::toString).toList()).doesNotHaveDuplicates();
        assertThat(body(call(get("/api/v1/ops/cooks").param("state", "DRAFT"), as(reviewerSubject))).get("items").size()).isZero();
        assertThat(call(get("/api/v1/ops/cooks").param("cursor", "%%%"), as(reviewerSubject)).getStatus()).isEqualTo(422);
        assertThat(body(call(get("/api/v1/ops/cooks").param("q", "synthetic kit").param("limit", "100"), as(reviewerSubject)))
                .get("items").size()).isGreaterThanOrEqualTo(3);
        assertThat(body(call(get("/api/v1/ops/cooks").param("q", "Synthetic%").param("limit", "100"), as(reviewerSubject)))
                .get("items").size()).as("wildcards in q are literal").isZero();

        UUID reviewer = id(reviewerSubject);
        MockHttpServletResponse detail = call(get("/api/v1/ops/cooks/" + submitted.getFirst()), as(reviewerSubject));
        assertThat(detail.getStatus()).isEqualTo(200);
        assertThat(detail.getContentAsString()).contains(LOCATION).contains(PHONE_E164);
        assertThat(audits(reviewer, "cook.application.view", "ALLOWED")).isEqualTo(1);
        assertThat(call(get("/api/v1/ops/cooks/" + UUID.randomUUID()), as(reviewerSubject)).getStatus()).isEqualTo(404);
    }

    // ---- TC-009: operating state separate from approval ----

    @Test
    void pause_stops_new_sales_suspension_stops_acceptance_and_accepted_orders_stay_fulfillable() throws Exception {
        UUID kitchen = approvedKitchen(applicantSubject);
        assertThat(kitchens.find(kitchen).orElseThrow().canSell()).isTrue();

        MockHttpServletResponse paused = operatingState(mfa(reviewerSubject), kitchen, "PAUSE", "Cook requested a holiday pause");
        assertThat(paused.getStatus()).as(paused.getContentAsString()).isEqualTo(200);
        KitchenStatus p = kitchens.find(kitchen).orElseThrow();
        assertThat(p.state()).isEqualTo(OperatingState.PAUSED);
        assertThat(p.canSell()).isFalse();
        assertThat(p.canAcceptOrders()).isTrue();
        assertThat(p.canFulfillAcceptedOrders()).isTrue();

        assertThat(operatingState(mfa(reviewerSubject), kitchen, "SUSPEND", "Safety complaint under review").getStatus()).isEqualTo(200);
        KitchenStatus s = kitchens.find(kitchen).orElseThrow();
        assertThat(s.canSell()).isFalse();
        assertThat(s.canAcceptOrders()).isFalse();
        assertThat(s.canFulfillAcceptedOrders()).isTrue();
        assertThat(jdbc.queryForObject("SELECT state FROM cook_application WHERE user_id = ?", String.class, id(applicantSubject)))
                .as("operating state is separate from the approval").isEqualTo("APPROVED");
        String slug = jdbc.queryForObject("SELECT public_slug FROM kitchen WHERE id = ?", String.class, kitchen);
        assertThat(body(call(get("/api/v1/kitchens/" + slug), null)).get("available").asBoolean()).isFalse();

        assertThat(operatingState(mfa(reviewerSubject), kitchen, "PAUSE", "invalid from suspension").getStatus()).isEqualTo(409);
        assertThat(operatingState(mfa(reviewerSubject), kitchen, "RESTORE", "Complaint resolved").getStatus()).isEqualTo(200);
        assertThat(kitchens.find(kitchen).orElseThrow().canSell()).isTrue();
        assertThat(audits(id(reviewerSubject), "kitchen.operating_state", "ALLOWED")).isEqualTo(3);
    }

    @Test
    void only_scoped_staff_change_operating_state_and_never_for_their_own_kitchen() throws Exception {
        UUID kitchen = approvedKitchen(applicantSubject);
        assertThat(operatingState(mfa(applicantSubject), kitchen, "RESTORE", "self clear").getStatus())
                .as("cook cannot clear or change state through ops").isEqualTo(403);
        assertThat(operatingState(as(reviewerSubject), kitchen, "SUSPEND", "no step-up").getStatus()).isEqualTo(403);
        MockHttpServletResponse noReason = call(post("/api/v1/ops/kitchens/" + kitchen + "/operating-state").content(
                "{\"action\":\"SUSPEND\",\"expectedVersion\":0}"), mfa(reviewerSubject));
        assertThat(noReason.getStatus()).isEqualTo(422);

        // A reviewer who is also an approved cook cannot act on their own kitchen.
        String secondReviewer = UUID.randomUUID().toString();
        grant(secondReviewer, "cook.review");
        UUID reviewerApp = underReview(secondReviewer);
        decideOk(reviewerApp, "APPROVE", "Operational checks complete", 1, version(reviewerApp));
        UUID ownKitchen = kitchens.ownedBy(id(secondReviewer)).orElseThrow().kitchenId();
        MockHttpServletResponse own = operatingState(mfa(secondReviewer), ownKitchen, "PAUSE", "pausing myself via ops");
        assertThat(own.getStatus()).isEqualTo(403);
        assertThat(audits(id(secondReviewer), "kitchen.operating_state", "DENIED")).isEqualTo(1);
        assertThat(kitchens.find(kitchen).orElseThrow().state()).isEqualTo(OperatingState.ACTIVE);
        assertThat(call(post("/api/v1/ops/kitchens/" + UUID.randomUUID() + "/operating-state").content(
                "{\"action\":\"PAUSE\",\"reason\":\"missing kitchen\",\"expectedVersion\":0}"), mfa(reviewerSubject)).getStatus())
                .isEqualTo(404);
    }

    @Test
    void public_profile_is_anonymous_and_never_exposes_private_location_or_contact() throws Exception {
        UUID kitchen = approvedKitchen(applicantSubject);
        String slug = jdbc.queryForObject("SELECT public_slug FROM kitchen WHERE id = ?", String.class, kitchen);
        MockHttpServletResponse r = call(get("/api/v1/kitchens/" + slug), null);
        assertThat(r.getStatus()).isEqualTo(200);
        String text = r.getContentAsString();
        assertThat(text).contains("Synthetic Kitchen").contains("OPERATIONALLY_VERIFIED").contains(zone.toString());
        assertThat(text).doesNotContain(LOCATION).doesNotContain(PHONE_E164).doesNotContain("01001234567")
                .doesNotContain(id(applicantSubject).toString()).doesNotContainIgnoringCase("certif");
        assertThat(call(get("/api/v1/kitchens/kitchen-doesnotexist"), null).getStatus()).isEqualTo(404);
        assertThat(call(get("/api/v1/kitchens/NOT_A_SLUG"), null).getStatus()).isEqualTo(404);
    }
}
