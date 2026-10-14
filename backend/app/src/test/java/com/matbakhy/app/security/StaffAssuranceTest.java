package com.matbakhy.app.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matbakhy.identity.api.AccountStatus;
import com.matbakhy.identity.api.CurrentAccount;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class StaffAssuranceTest {

    private static final Instant NOW = Instant.parse("2026-10-11T10:00:00Z");
    private final IdentityProperties properties = new IdentityProperties("https://idp.test.invalid/realms/x", null, "matbakhy-api",
            "web", "s", List.of("http://localhost:5173"), "http://localhost:5173/", List.of("mfa", "2"), Duration.ofMinutes(10), null, null);
    private final StaffAssurance assurance = new StaffAssurance(properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private static AccountAuthentication auth(AccountStatus status, Map<String, Object> claims) {
        return new AccountAuthentication(new CurrentAccount(UUID.randomUUID(), status, "Staff", "ar", true, false, 0), claims, List.of());
    }

    @Test
    void recent_mfa_acr_passes() {
        assertThat(assurance.recentMfa(auth(AccountStatus.ACTIVE, Map.of("acr", "2", "auth_time", NOW.minusSeconds(60).getEpochSecond())))).isTrue();
        assertThat(assurance.recentMfa(auth(AccountStatus.ACTIVE, Map.of("acr", "1", "amr", List.of("pwd", "otp"),
                "auth_time", NOW.minusSeconds(60).getEpochSecond())))).isTrue();
    }

    @Test
    void password_only_stale_or_suspended_is_refused() {
        assertThatThrownBy(() -> assurance.recentMfa(auth(AccountStatus.ACTIVE, Map.of("acr", "1", "auth_time", NOW.getEpochSecond()))))
                .isInstanceOf(StepUpRequiredException.class);
        assertThatThrownBy(() -> assurance.recentMfa(auth(AccountStatus.ACTIVE, Map.of("acr", "2",
                "auth_time", NOW.minus(Duration.ofMinutes(11)).getEpochSecond())))).isInstanceOf(StepUpRequiredException.class);
        assertThatThrownBy(() -> assurance.recentMfa(auth(AccountStatus.ACTIVE, Map.of("acr", "2")))).isInstanceOf(StepUpRequiredException.class);
        assertThat(assurance.recentMfa(auth(AccountStatus.SUSPENDED, Map.of("acr", "2", "auth_time", NOW.getEpochSecond())))).isFalse();
    }
}
