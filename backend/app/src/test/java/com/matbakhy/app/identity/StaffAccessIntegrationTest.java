package com.matbakhy.app.identity;

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
import com.matbakhy.identity.api.AccessPolicy;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.Redaction;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * TC-006, TC-007, TC-111: deny-by-default object scope, server-side grants, step-up, revocation taking
 * effect on the next request, last-administrator protection, append-only redacted audit. Each call re-maps
 * the identity exactly as production does per request.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({TestLogins.class, StaffAccessIntegrationTest.Beans.class})
class StaffAccessIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        KitchenProbe kitchenProbe(AccessPolicy policy) {
            return new KitchenProbe(policy);
        }
    }

    /** Stand-in for kitchen-owned resources (MB-012+) to exercise the scope policy across two cooks. */
    @RestController
    static class KitchenProbe {
        private final AccessPolicy policy;

        KitchenProbe(AccessPolicy policy) {
            this.policy = policy;
        }

        @GetMapping("/api/v1/test-scope/kitchens/{ownerId}")
        String kitchen(@PathVariable UUID ownerId) {
            policy.requireOwnerOrPermitted(ownerId, Permission.COOK_REVIEW, "kitchen", ownerId.toString());
            return "private kitchen data of " + ownerId;
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;

    String adminSubject;
    String staffSubject;
    String customerSubject;
    String cookASubject;
    String cookBSubject;

    @BeforeEach
    void setUp() {
        adminSubject = UUID.randomUUID().toString();
        staffSubject = UUID.randomUUID().toString();
        customerSubject = UUID.randomUUID().toString();
        cookASubject = UUID.randomUUID().toString();
        cookBSubject = UUID.randomUUID().toString();
        // Each test starts with exactly one administrator: revoke any staff.grant left by other tests.
        UUID admin = logins.login(adminSubject, "Admin").getPrincipal().id();
        jdbc.update("UPDATE user_grant SET revoked_at = now(), revoked_by = user_id, revoke_reason = 'test isolation' "
                + "WHERE permission = 'staff.grant' AND revoked_at IS NULL");
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'staff.grant', 'test admin'), "
                + "(?, ?, 'audit.read', 'test admin')", UUID.randomUUID(), admin, UUID.randomUUID(), admin);
    }

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "User");
    }

    private AccountAuthentication asMfa(String subject) {
        return logins.loginWithMfa(subject, "User");
    }

    private UUID id(String subject) {
        return as(subject).getPrincipal().id();
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        return mvc.perform(r.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private MockHttpServletResponse grant(AccountAuthentication actor, UUID target, String permission, String reason) throws Exception {
        return call(post("/api/v1/ops/staff/" + target + "/grants").content(
                "{\"permission\":\"" + permission + "\",\"reason\":\"" + reason + "\"}"), actor);
    }

    private int audits(UUID actor, String outcome) {
        return jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND outcome = ?", Integer.class, actor, outcome);
    }

    private static String field(String body, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).as(name + " in " + body).isTrue();
        return m.group(1);
    }

    // ---- TC-007: privilege escalation attempts ----

    @Test
    void customer_cannot_grant_privileges_and_the_attempt_is_audited() throws Exception {
        UUID customer = id(customerSubject);
        MockHttpServletResponse r = grant(asMfa(customerSubject), customer, "staff.grant", "please make me admin");
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_grant WHERE user_id = ?", Integer.class, customer)).isZero();
        assertThat(audits(customer, "DENIED")).isEqualTo(1);
        assertThat(call(get("/api/v1/ops/audit"), as(customerSubject)).getStatus()).isEqualTo(403);
    }

    @Test
    void role_fields_in_customer_requests_are_rejected() throws Exception {
        MockHttpServletResponse r = call(patch("/api/v1/me").content("{\"expectedVersion\":0,\"role\":\"ADMIN\"}"), as(customerSubject));
        assertThat(r.getStatus()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_grant WHERE user_id = ?", Integer.class, id(customerSubject))).isZero();
    }

    @Test
    void grant_changes_require_recent_mfa() throws Exception {
        MockHttpServletResponse r = grant(as(adminSubject), id(staffSubject), "audit.read", "needs audit access");
        assertThat(r.getStatus()).isEqualTo(403);
        assertThat(r.getContentAsString()).contains("STEP_UP_REQUIRED");
        assertThat(audits(id(adminSubject), "DENIED")).isGreaterThanOrEqualTo(1);
    }

    @Test
    void revoked_grant_stops_working_on_the_next_request() throws Exception {
        UUID staff = id(staffSubject);
        MockHttpServletResponse granted = grant(asMfa(adminSubject), staff, "audit.read", "support audit duty");
        assertThat(granted.getStatus()).as(granted.getContentAsString()).isEqualTo(201);
        String grantId = field(granted.getContentAsString(), "id");
        assertThat(call(get("/api/v1/ops/audit"), as(staffSubject)).getStatus()).isEqualTo(200);

        MockHttpServletResponse revoked = call(post("/api/v1/ops/staff/" + staff + "/grant-revocations")
                .content("{\"grantId\":\"" + grantId + "\",\"reason\":\"duty ended\"}"), asMfa(adminSubject));
        assertThat(revoked.getStatus()).isEqualTo(200);
        MockHttpServletResponse reused = call(get("/api/v1/ops/audit"), as(staffSubject));
        assertThat(reused.getStatus()).isEqualTo(403);
        assertThat(audits(staff, "DENIED")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action = 'staff.revoke' AND object_id = ?",
                Integer.class, staff.toString())).isEqualTo(1);
    }

    @Test
    void self_escalation_unknown_permissions_and_last_admin_are_refused() throws Exception {
        UUID admin = id(adminSubject);
        MockHttpServletResponse self = grant(asMfa(adminSubject), admin, "finance.verify", "self service");
        assertThat(self.getStatus()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND reason = 'self-escalation refused'",
                Integer.class, admin)).isEqualTo(1);
        assertThat(grant(asMfa(adminSubject), id(staffSubject), "ADMIN", "superuser please").getStatus()).isEqualTo(422);

        String adminGrant = jdbc.queryForObject("SELECT id::text FROM user_grant WHERE user_id = ? AND permission = 'staff.grant' AND revoked_at IS NULL",
                String.class, admin);
        MockHttpServletResponse last = call(post("/api/v1/ops/staff/" + admin + "/grant-revocations")
                .content("{\"grantId\":\"" + adminGrant + "\",\"reason\":\"leaving\"}"), asMfa(adminSubject));
        assertThat(last.getStatus()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_grant WHERE id = ?::uuid AND revoked_at IS NULL", Integer.class, adminGrant))
                .isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND reason = 'last administrator protected'",
                Integer.class, admin)).isEqualTo(1);
    }

    @Test
    void expired_grants_confer_nothing() throws Exception {
        UUID staff = id(staffSubject);
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason, granted_at, expires_at) "
                + "VALUES (?, ?, 'audit.read', 'expired duty', now() - interval '2 days', now() - interval '1 day')", UUID.randomUUID(), staff);
        assertThat(call(get("/api/v1/ops/audit"), as(staffSubject)).getStatus()).isEqualTo(403);
    }

    // ---- audit integrity and redaction ----

    @Test
    void audit_is_append_only_and_redacted() throws Exception {
        UUID staff = id(staffSubject);
        MockHttpServletResponse r = grant(asMfa(adminSubject), staff, "support.case",
                "requested by +201012345678 via ops-lead@matbakhy.test");
        assertThat(r.getStatus()).isEqualTo(201);
        Map<String, Object> row = jdbc.queryForMap("SELECT id, reason, details::text AS details FROM audit_event "
                + "WHERE action = 'staff.grant' AND object_id = ? ORDER BY occurred_at DESC LIMIT 1", staff.toString());
        assertThat((String) row.get("reason")).contains(Redaction.MASK).doesNotContain("1012345678", "ops-lead", "@");
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET reason = 'tampered' WHERE id = ?", row.get("id")))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE id = ?", row.get("id"))).hasMessageContaining("append-only");

        MockHttpServletResponse page = call(get("/api/v1/ops/audit?objectId=" + staff), as(adminSubject));
        assertThat(page.getStatus()).isEqualTo(200);
        assertThat(page.getContentAsString()).contains("staff.grant").doesNotContain("1012345678");
        // MB-039: reading the audit trail is itself audited, with the filter used and without including itself in the page.
        assertThat(page.getContentAsString()).doesNotContain("\"action\":\"audit.read\"");
        assertThat(jdbc.queryForList("SELECT actor_id::text || '|' || outcome || '|' || object_id FROM audit_event WHERE action = 'audit.read' AND object_id = ?",
                String.class, staff.toString())).containsExactly(id(adminSubject) + "|ALLOWED|" + staff);
    }

    @Test
    void redaction_masks_sensitive_detail_keys_and_values() {
        Map<String, Object> out = Redaction.details(Map.of("otpCode", "123456", "accessToken", "eyJa.b.c", "note",
                "contact ٠١٠١٢٣٤٥٦٧٨ or a@b.co", "count", 3));
        assertThat(out.get("otpCode")).isEqualTo(Redaction.MASK);
        assertThat(out.get("accessToken")).isEqualTo(Redaction.MASK);
        assertThat((String) out.get("note")).doesNotContain("٠١٠١٢٣٤٥٦٧٨", "a@b.co");
        assertThat(out.get("count")).isEqualTo(3);
    }

    // ---- TC-006: object scope across customers, cooks and staff ----

    @Test
    void kitchen_scope_matrix() throws Exception {
        UUID cookA = id(cookASubject);
        String path = "/api/v1/test-scope/kitchens/" + cookA;
        assertThat(call(get(path), as(cookASubject)).getStatus()).isEqualTo(200);
        for (String other : List.of(cookBSubject, customerSubject, staffSubject)) {
            MockHttpServletResponse denied = call(get(path), as(other));
            assertThat(denied.getStatus()).as(other).isEqualTo(404);
            assertThat(denied.getContentAsString()).doesNotContain("private kitchen data");
        }
        UUID staff = id(staffSubject);
        jdbc.update("INSERT INTO user_grant (id, user_id, permission, grant_reason) VALUES (?, ?, 'cook.review', 'reviewer')",
                UUID.randomUUID(), staff);
        assertThat(call(get(path), as(staffSubject)).getStatus()).isEqualTo(200);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE actor_id = ? AND action = 'access.kitchen' AND outcome = 'ALLOWED'",
                Integer.class, staff)).isEqualTo(1);
        assertThat(call(get("/api/v1/ops/staff"), as(staffSubject)).getStatus()).as("cook.review is not staff.grant").isEqualTo(403);
        assertThat(call(get("/api/v1/ops/staff"), as(adminSubject)).getContentAsString()).contains(staff.toString(), "cook.review");
    }
}
