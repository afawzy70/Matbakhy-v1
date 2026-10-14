package com.matbakhy.app.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.providers.FakePhoneVerificationSender;
import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.MutableClock;
import com.matbakhy.identity.api.CheckoutContactPolicy;
import com.matbakhy.identity.api.PhoneVerificationSender;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/** TC-003, TC-004, TC-005, TC-110 on real PostgreSQL through the real security chain. */
@IntegrationTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@Import({ContactIntegrationTest.Beans.class, TestLogins.class})
class ContactIntegrationTest {

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired PhoneVerificationSender sender;
    @Autowired CheckoutContactPolicy checkout;
    @Autowired Clock clock;

    AccountAuthentication a;
    AccountAuthentication b;
    UUID zone;
    UUID inactiveZone;

    @BeforeEach
    void setUp() {
        a = logins.newCustomer("Customer A");
        b = logins.newCustomer("Customer B");
        zone = zone(true);
        inactiveZone = zone(false);
    }

    @AfterEach
    void resetClock() {
        ((MutableClock) clock).reset();
    }

    private UUID zone(boolean active) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                + "VALUES (?, ?, 'منطقة اختبار', 'Test zone', 'مدينة', 'City', ?, 3000)", id, "z-" + id.toString().substring(0, 8), active);
        return id;
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder request, AccountAuthentication who) throws Exception {
        RequestBuilder r = request.with(authentication(who)).with(csrf()).contentType(MediaType.APPLICATION_JSON);
        return mvc.perform(r).andReturn().getResponse();
    }

    private static String field(String body, String name) {
        Matcher m = Pattern.compile("\"" + name + "\":\"([^\"]+)\"").matcher(body);
        assertThat(m.find()).as(name + " in " + body).isTrue();
        return m.group(1);
    }

    private String startChallenge(AccountAuthentication who, String phone) throws Exception {
        MockHttpServletResponse r = call(post("/api/v1/me/phone/challenges").content("{\"phoneE164\":\"" + phone + "\"}"), who);
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(201);
        return field(r.getContentAsString(), "id");
    }

    private MockHttpServletResponse verify(AccountAuthentication who, String challenge, String code) throws Exception {
        return call(post("/api/v1/me/phone/challenges/" + challenge + "/verify").content("{\"code\":\"" + code + "\"}"), who);
    }

    private String code(String e164) {
        return ((FakePhoneVerificationSender) sender).lastCode(e164).orElseThrow();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private void advance(Duration d) {
        ((MutableClock) clock).advance(d);
    }

    private boolean phoneVerified(AccountAuthentication who) {
        return jdbc.queryForObject("SELECT phone_verified_at IS NOT NULL FROM app_user WHERE id = ?", Boolean.class, who.getPrincipal().id());
    }

    // ---- TC-004: OTP lifecycle ----

    @Test
    void correct_code_verifies_once_and_replay_fails(CapturedOutput output) throws Exception {
        String challenge = startChallenge(a, "01012345678");
        String code = code("+201012345678");
        assertThat(verify(a, challenge, wrong(code)).getStatus()).isEqualTo(422);
        assertThat(phoneVerified(a)).isFalse();
        MockHttpServletResponse ok = verify(a, challenge, code);
        assertThat(ok.getStatus()).isEqualTo(200);
        assertThat(phoneVerified(a)).isTrue();
        MockHttpServletResponse replay = verify(a, challenge, code);
        assertThat(replay.getStatus()).isEqualTo(422);
        assertThat(replay.getContentAsString()).contains("\"code\":\"VERIFICATION_FAILED\"").doesNotContain(code);
        assertThat(jdbc.queryForObject("SELECT code_hmac FROM phone_challenge WHERE id = ?::uuid", String.class, challenge))
                .doesNotContain(code).hasSize(64);
        assertThat(output.getAll()).doesNotContain(code);
    }

    @Test
    void expired_code_fails_and_does_not_verify(CapturedOutput output) throws Exception {
        String challenge = startChallenge(a, "01112345678");
        String code = code("+201112345678");
        advance(Duration.ofMinutes(5).plusSeconds(1));
        MockHttpServletResponse r = verify(a, challenge, code);
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).contains("VERIFICATION_FAILED");
        assertThat(phoneVerified(a)).isFalse();
        assertThat(output.getAll()).doesNotContain(code);
    }

    @Test
    void challenge_locks_after_five_failed_attempts() throws Exception {
        String challenge = startChallenge(a, "01212345678");
        String code = code("+201212345678");
        for (int i = 0; i < 5; i++) {
            assertThat(verify(a, challenge, wrong(code)).getStatus()).isEqualTo(422);
        }
        MockHttpServletResponse locked = verify(a, challenge, code);
        assertThat(locked.getStatus()).isEqualTo(429);
        assertThat(locked.getContentAsString()).contains("RATE_LIMITED");
        assertThat(phoneVerified(a)).isFalse();
    }

    @Test
    void resend_interval_and_hourly_limits_are_enforced() throws Exception {
        startChallenge(a, "01512345678");
        MockHttpServletResponse tooSoon = call(post("/api/v1/me/phone/challenges").content("{\"phoneE164\":\"01512345678\"}"), a);
        assertThat(tooSoon.getStatus()).isEqualTo(429);
        for (int i = 0; i < 4; i++) {
            advance(Duration.ofSeconds(61));
            startChallenge(a, "01512345678");
        }
        advance(Duration.ofSeconds(61));
        MockHttpServletResponse hourly = call(post("/api/v1/me/phone/challenges").content("{\"phoneE164\":\"01512345678\"}"), a);
        assertThat(hourly.getStatus()).as("6th send within an hour").isEqualTo(429);
        advance(Duration.ofSeconds(61));
        MockHttpServletResponse otherAccountSameNumber = call(post("/api/v1/me/phone/challenges").content("{\"phoneE164\":\"01512345678\"}"), b);
        assertThat(otherAccountSameNumber.getStatus()).as("per-number limit across accounts").isEqualTo(429);
    }

    @Test
    void another_accounts_challenge_is_not_found() throws Exception {
        String challenge = startChallenge(a, "01098765432");
        MockHttpServletResponse r = verify(b, challenge, code("+201098765432"));
        assertThat(r.getStatus()).isEqualTo(404);
        assertThat(phoneVerified(a)).isFalse();
        assertThat(phoneVerified(b)).isFalse();
    }

    @Test
    void invalid_numbers_are_rejected_without_sending() throws Exception {
        MockHttpServletResponse r = call(post("/api/v1/me/phone/challenges").content("{\"phoneE164\":\"0223456789\"}"), a);
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).contains("EGYPTIAN_MOBILE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM phone_challenge WHERE user_id = ?", Integer.class, a.getPrincipal().id())).isZero();
    }

    // ---- TC-003: changed phone needs verification again ----

    @Test
    void changing_a_verified_phone_requires_verifying_the_new_number() throws Exception {
        UUID id = a.getPrincipal().id();
        assertThatThrownBy(() -> checkout.requireVerifiedContact(id))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTACT_VERIFICATION_REQUIRED));
        String first = startChallenge(a, "01011112222");
        assertThat(verify(a, first, code("+201011112222")).getStatus()).isEqualTo(200);
        checkout.requireVerifiedContact(id);

        advance(Duration.ofSeconds(61));
        String second = startChallenge(a, "01033334444");
        assertThat(jdbc.queryForObject("SELECT phone_e164 FROM app_user WHERE id = ?", String.class, id)).isEqualTo("+201033334444");
        assertThat(phoneVerified(a)).isFalse();
        assertThatThrownBy(() -> checkout.requireVerifiedContact(id))
                .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo(ErrorCode.CONTACT_VERIFICATION_REQUIRED));

        assertThat(verify(a, second, code("+201033334444")).getStatus()).isEqualTo(200);
        checkout.requireVerifiedContact(id);
    }

    @Test
    void code_for_a_previous_number_cannot_verify_a_newer_number() throws Exception {
        String old = startChallenge(a, "01055556666");
        String oldCode = code("+201055556666");
        advance(Duration.ofSeconds(61));
        startChallenge(a, "01077778888");
        assertThat(verify(a, old, oldCode).getStatus()).isEqualTo(422);
        assertThat(phoneVerified(a)).isFalse();
    }

    // ---- TC-005: address ownership ----

    private String address(String extra) {
        return "{\"label\":\"البيت\",\"recipientName\":\"عميل تجريبي\",\"phone\":\"01012345678\",\"governorate\":\"محافظة تجريبية\","
                + "\"city\":\"مدينة تجريبية\",\"zoneId\":\"" + zone + "\",\"street\":\"شارع ١\",\"building\":\"٤\"" + extra + "}";
    }

    @Test
    void addresses_are_private_to_their_owner() throws Exception {
        MockHttpServletResponse created = call(post("/api/v1/me/addresses").content(address(",\"latitude\":30.044420,\"longitude\":31.235712")), a);
        assertThat(created.getStatus()).as(created.getContentAsString()).isEqualTo(201);
        String id = field(created.getContentAsString(), "id");
        assertThat(created.getContentAsString()).contains("\"phoneE164\":\"+201012345678\"", "\"zoneId\":\"" + zone + "\"");

        assertThat(call(get("/api/v1/me/addresses"), a).getContentAsString()).contains(id);
        MockHttpServletResponse otherList = call(get("/api/v1/me/addresses"), b);
        assertThat(otherList.getContentAsString()).doesNotContain(id, "عميل تجريبي");

        MockHttpServletResponse otherEdit = call(patch("/api/v1/me/addresses/" + id).content("{\"expectedVersion\":0,\"street\":\"hijack\"}"), b);
        assertThat(otherEdit.getStatus()).isEqualTo(404);
        assertThat(otherEdit.getContentAsString()).doesNotContain("عميل تجريبي");
        assertThat(call(delete("/api/v1/me/addresses/" + id), b).getStatus()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT street FROM address WHERE id = ?::uuid", String.class, id)).isEqualTo("شارع ١");
        assertThat(jdbc.queryForObject("SELECT archived_at IS NULL FROM address WHERE id = ?::uuid", Boolean.class, id)).isTrue();
    }

    @Test
    void address_validation_and_versioning() throws Exception {
        assertThat(call(post("/api/v1/me/addresses").content(address("").replace(zone.toString(), inactiveZone.toString())), a)
                .getContentAsString()).contains("ZONE_NOT_SERVED");
        assertThat(call(post("/api/v1/me/addresses").content(address("").replace("01012345678", "0223456789")), a)
                .getStatus()).isEqualTo(422);
        assertThat(call(post("/api/v1/me/addresses").content(address(",\"latitude\":30.1")), a).getContentAsString())
                .contains("PAIR_REQUIRED");
        assertThat(call(post("/api/v1/me/addresses").content(address("").replace(",\"building\":\"٤\"", "")), a).getStatus())
                .isEqualTo(422);
        assertThat(call(post("/api/v1/me/addresses").content(address(",\"userId\":\"" + b.getPrincipal().id() + "\"")), a).getStatus())
                .as("privileged field rejected").isEqualTo(400);

        String id = field(call(post("/api/v1/me/addresses").content(address("")), a).getContentAsString(), "id");
        MockHttpServletResponse updated = call(patch("/api/v1/me/addresses/" + id).content("{\"expectedVersion\":0,\"floor\":\"٣\"}"), a);
        assertThat(updated.getStatus()).isEqualTo(200);
        assertThat(updated.getContentAsString()).contains("\"floor\":\"٣\"", "\"version\":1");
        assertThat(call(patch("/api/v1/me/addresses/" + id).content("{\"expectedVersion\":0,\"floor\":\"٥\"}"), a).getStatus()).isEqualTo(409);

        assertThat(call(delete("/api/v1/me/addresses/" + id), a).getStatus()).isEqualTo(204);
        assertThat(call(get("/api/v1/me/addresses"), a).getContentAsString()).doesNotContain(id);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM address WHERE id = ?::uuid", Integer.class, id)).as("soft archive").isEqualTo(1);
    }

    // ---- deletion intake ----

    @Test
    void deletion_request_disables_access_and_is_idempotent() throws Exception {
        assertThat(call(post("/api/v1/me/deletion-requests").content("{\"confirm\":false,\"policyVersion\":\"draft-1\"}"), a).getStatus())
                .isEqualTo(422);
        MockHttpServletResponse first = call(post("/api/v1/me/deletion-requests").content("{\"confirm\":true,\"policyVersion\":\"draft-1\"}"), a);
        assertThat(first.getStatus()).isEqualTo(200);
        String id = field(first.getContentAsString(), "id");

        AccountAuthentication again = logins.login(jdbc.queryForObject("SELECT subject FROM app_user WHERE id = ?", String.class,
                a.getPrincipal().id()), "Customer A");
        MockHttpServletResponse me = call(get("/api/v1/me"), again);
        assertThat(me.getStatus()).isEqualTo(403);
        assertThat(me.getContentAsString()).contains("ACCOUNT_DISABLED").doesNotContain("Customer A");
        MockHttpServletResponse retry = call(post("/api/v1/me/deletion-requests").content("{\"confirm\":true,\"policyVersion\":\"draft-1\"}"), again);
        assertThat(retry.getStatus()).isEqualTo(200);
        assertThat(field(retry.getContentAsString(), "id")).isEqualTo(id);
    }
}
