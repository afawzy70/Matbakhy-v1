package com.matbakhy.app.identity;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.KeycloakTestServer;
import com.matbakhy.app.support.MfaBrowser;
import com.matbakhy.app.support.MfaBrowser.Enrolment;
import com.matbakhy.app.support.MfaBrowser.Outcome;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;

/**
 * D18 against real Keycloak (the realm files under infra/ and deploy/ carry the same flow): a password login never asks for a
 * second factor, an authorization that asks for acr "mfa" (step-up) must pass OTP, an account without a factor is led to
 * enrolment instead of being admitted, and the token then asserts it. Each test uses its own account because Keycloak
 * refuses to accept one TOTP code twice. Synthetic accounts; password and TOTP secret are generated per run.
 */
class MfaIdpTest {

    private static final String PW = KeycloakTestServer.MFA_PASSWORD;
    private static final String SECRET = KeycloakTestServer.MFA_TOTP_SECRET;

    @Test
    void a_normal_sign_in_never_asks_for_a_second_factor_even_when_one_is_enrolled() throws Exception {
        Outcome o = MfaBrowser.login("otp-login", PW, null, false);
        assertThat(o.issuedCode()).isTrue();
        assertThat(MfaBrowser.claims(MfaBrowser.accessToken(o))).doesNotContain("\"otp\"");
    }

    @Test
    void step_up_with_only_a_password_never_receives_an_authorization_code() throws Exception {
        Outcome o = MfaBrowser.login("otp-login", PW, null, true);
        assertThat(o.issuedCode()).isFalse();
        assertThat(o.asksForOtp()).isTrue();
    }

    @Test
    void a_wrong_one_time_code_is_refused() throws Exception {
        String valid = MfaBrowser.now(SECRET);
        String wrong = String.format("%06d", (Integer.parseInt(valid) + 1) % 1_000_000);
        assertThat(MfaBrowser.login("otp-stepup", PW, wrong, true).issuedCode()).isFalse();
    }

    @Test
    void an_account_without_a_factor_is_led_to_enrolment_not_admitted() throws Exception {
        Outcome o = MfaBrowser.login("no-otp", PW, null, true);
        assertThat(o.issuedCode()).as("step-up without a factor must not mean no factor needed").isFalse();
        assertThat(o.asksForEnrolment()).isTrue();
    }

    @Test
    void enrolment_through_the_provider_finishes_the_login_and_the_token_asserts_otp() throws Exception {
        Outcome pending = MfaBrowser.login("enrol", PW, null, true);
        Enrolment enrolment = MfaBrowser.enrol(pending, "synthetic phone");
        assertThat(enrolment.finished().issuedCode()).isTrue();
        // Enrolment reaches the second authentication level (acr "mfa"); the amr list only gains "otp" when the OTP form itself is answered.
        assertThat(MfaBrowser.claims(MfaBrowser.accessToken(enrolment.finished()))).contains("\"acr\":\"mfa\"").contains("\"auth_time\"");
    }

    @Test
    void a_valid_one_time_code_completes_step_up_and_the_token_asserts_otp() throws Exception {
        Outcome o = MfaBrowser.login("otp-stepup", PW, MfaBrowser.now(SECRET), true);
        assertThat(o.issuedCode()).isTrue();
        String claims = MfaBrowser.claims(MfaBrowser.accessToken(o));
        assertThat(claims).containsPattern("\"amr\":\\[[^\\]]*\"otp\"").contains("\"acr\":\"mfa\"");
    }

    @Test
    void an_existing_password_only_session_must_still_pass_otp_when_the_client_steps_up() throws Exception {
        Outcome first = MfaBrowser.login("otp-sso", PW, null, false);
        assertThat(first.issuedCode()).isTrue();
        // Same browser (SSO cookie, level 1): a step-up authorization asks for the OTP instead of issuing a code.
        Outcome stepUp = MfaBrowser.login(first.browser(), "otp-sso", PW, null, true);
        assertThat(stepUp.issuedCode()).as("no second factor from the SSO cookie alone").isFalse();
        assertThat(stepUp.asksForOtp()).isTrue();
        Outcome done = MfaBrowser.submitOtp(stepUp, MfaBrowser.now(SECRET));
        assertThat(done.issuedCode()).isTrue();
    }

    @Test
    void customers_without_a_factor_keep_password_only_sign_in_and_sso() throws Exception {
        Outcome first = MfaBrowser.login("customer-a", KeycloakTestServer.PASSWORD_A, null, false);
        assertThat(first.issuedCode()).isTrue();
        HttpResponse<String> again = first.browser().get(MfaBrowser.authorizeUrl(first.verifier(), false));
        assertThat(again.headers().firstValue("Location").orElse("")).contains("code=");
    }
}
