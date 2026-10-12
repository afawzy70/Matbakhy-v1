package com.matbakhy.app.providers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matbakhy.app.config.MatbakhyProperties;
import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import com.matbakhy.identity.internal.PhoneVerificationProperties;
import com.matbakhy.notifications.internal.NotificationProperties;
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

    @Test
    void notification_channels_only_in_local_and_ci_and_dev_token_keys_refused_elsewhere() {
        ProviderConfiguration config = new ProviderConfiguration();
        NotificationProperties dev = new NotificationProperties("local-dev-only-push-token-hash-key-not-secret",
                "local-dev-only-push-token-encryption-key-not-secret", 5, Duration.ofSeconds(30), Duration.ofSeconds(60));
        NotificationProperties real = new NotificationProperties("a-real-hash-key-value-0123456789abcdef",
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 5, Duration.ofSeconds(30), Duration.ofSeconds(60));
        assertThat(config.emailSender(env(DeploymentEnvironment.CI))).isNotNull();
        assertThat(config.pushSender(env(DeploymentEnvironment.LOCAL), dev)).isNotNull();
        assertThatThrownBy(() -> config.pushSender(env(DeploymentEnvironment.PRODUCTION), dev)).hasMessageContaining("PUSH_TOKEN_HASH_KEY");
        assertThatThrownBy(() -> config.pushSender(env(DeploymentEnvironment.STAGING), real)).hasMessageContaining("No push notification provider");
        assertThatThrownBy(() -> config.emailSender(env(DeploymentEnvironment.PRODUCTION))).hasMessageContaining("No email notification provider");
    }

    /** PREVIEW keeps synthetic providers (nothing is ever sent) but demands real secrets and real S3-compatible storage. */
    @Test
    void preview_uses_synthetic_providers_with_real_secrets_and_storage() {
        ProviderConfiguration config = new ProviderConfiguration();
        MatbakhyProperties preview = env(DeploymentEnvironment.PREVIEW);
        assertThat(config.phoneVerificationSender(preview, phone("a-real-secret-pepper-value-0123456789abcdef")))
                .isInstanceOf(FakePhoneVerificationSender.class);
        assertThatThrownBy(() -> config.phoneVerificationSender(preview, phone("local-dev-only-pepper-not-a-secret-000000")))
                .hasMessageContaining("PHONE_CODE_PEPPER");
        assertThat(config.malwareScanner(preview)).isInstanceOf(FakeMalwareScanner.class);
        assertThat(config.emailSender(preview)).isNotNull();
        NotificationProperties dev = new NotificationProperties("local-dev-only-push-token-hash-key-not-secret",
                "local-dev-only-push-token-encryption-key-not-secret", 5, Duration.ofSeconds(30), Duration.ofSeconds(60));
        NotificationProperties real = new NotificationProperties("a-real-hash-key-value-0123456789abcdef",
                "MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=", 5, Duration.ofSeconds(30), Duration.ofSeconds(60));
        assertThat(config.pushSender(preview, real)).isNotNull();
        assertThatThrownBy(() -> config.pushSender(preview, dev)).hasMessageContaining("PUSH_TOKEN_HASH_KEY");
        StorageProperties emulator = new StorageProperties(java.net.URI.create("http://storage:9000"), null, "eu-central-1", "local-emulator-only",
                "local-emulator-only", "private", "public", java.net.URI.create("https://media.example.test"));
        assertThatThrownBy(() -> config.objectStorage(preview, emulator)).hasMessageContaining("Object storage credentials");
    }
}
