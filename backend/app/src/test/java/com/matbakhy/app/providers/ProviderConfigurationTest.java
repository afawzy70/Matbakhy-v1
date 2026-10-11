package com.matbakhy.app.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matbakhy.app.config.MatbakhyProperties;
import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import com.matbakhy.identity.internal.PhoneVerificationProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class ProviderConfigurationTest {

    private static MatbakhyProperties env(DeploymentEnvironment e) {
        return new MatbakhyProperties(e, new MatbakhyProperties.Market("EGP", "Africa/Cairo"));
    }

    private static PhoneVerificationProperties phone(String pepper) {
        return new PhoneVerificationProperties(pepper, Duration.ofMinutes(5), 5, Duration.ofSeconds(60), 5, 5);
    }

    @Test
    void fake_sender_only_in_local_and_ci() {
        ProviderConfiguration config = new ProviderConfiguration();
        assertThat(config.phoneVerificationSender(env(DeploymentEnvironment.LOCAL), phone("local-dev-only-pepper-not-a-secret-000000")))
                .isInstanceOf(FakePhoneVerificationSender.class);
        assertThat(config.phoneVerificationSender(env(DeploymentEnvironment.CI), phone("local-dev-only-pepper-not-a-secret-000000")))
                .isInstanceOf(FakePhoneVerificationSender.class);
        assertThatThrownBy(() -> config.phoneVerificationSender(env(DeploymentEnvironment.PRODUCTION),
                phone("local-dev-only-pepper-not-a-secret-000000"))).hasMessageContaining("PHONE_CODE_PEPPER");
        assertThatThrownBy(() -> config.phoneVerificationSender(env(DeploymentEnvironment.STAGING),
                phone("a-real-secret-pepper-value-0123456789abcdef"))).hasMessageContaining("No SMS verification provider");
    }
}
