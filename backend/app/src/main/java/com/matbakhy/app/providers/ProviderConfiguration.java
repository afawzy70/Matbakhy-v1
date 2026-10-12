package com.matbakhy.app.providers;

import com.matbakhy.app.config.MatbakhyProperties;
import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import com.matbakhy.catalog.api.MalwareScanner;
import com.matbakhy.catalog.api.ObjectStorage;
import com.matbakhy.notifications.api.NotificationChannels.EmailSender;
import com.matbakhy.notifications.api.NotificationChannels.PushSender;
import com.matbakhy.notifications.internal.NotificationProperties;
import com.matbakhy.identity.api.PhoneVerificationSender;
import com.matbakhy.identity.internal.PhoneVerificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provider adapters (ADR-004). Fakes exist only for LOCAL/CI/PREVIEW (synthetic data); staging and production fail fast without a real provider. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties({PhoneVerificationProperties.class, StorageProperties.class})
class ProviderConfiguration {

    static final String DEV_PEPPER_PREFIX = "local-dev-only-";

    @Bean
    PhoneVerificationSender phoneVerificationSender(MatbakhyProperties platform, PhoneVerificationProperties phone) {
        DeploymentEnvironment env = platform.environment();
        if (!env.developmentSecrets() && phone.codePepper().startsWith(DEV_PEPPER_PREFIX)) {
            throw new IllegalStateException("PHONE_CODE_PEPPER must be set to a secret value in " + env);
        }
        if (env.synthetic()) {
            return new FakePhoneVerificationSender();
        }
        if (phone.codePepper().startsWith(DEV_PEPPER_PREFIX)) {
            throw new IllegalStateException("PHONE_CODE_PEPPER must be set to a secret value in " + env);
        }
        throw new IllegalStateException("No SMS verification provider is configured for " + env + " (owner decision O07)");
    }

    static final String LOCAL_STORAGE_KEY = "local-emulator-only";

    @Bean
    ObjectStorage objectStorage(MatbakhyProperties platform, StorageProperties storage) {
        boolean local = platform.environment().developmentSecrets();
        if (storage.endpoint() == null || storage.endpoint().toString().isBlank()) {
            throw new IllegalStateException("OBJECT_STORAGE_ENDPOINT is required");
        }
        if (!local && (storage.accessKey() == null || storage.accessKey().isBlank() || storage.secretKey() == null
                || storage.secretKey().isBlank() || LOCAL_STORAGE_KEY.equals(storage.accessKey()) || !storage.hasPublicBaseUrl())) {
            throw new IllegalStateException("Object storage credentials and public base URL must be configured for "
                    + platform.environment() + " (provider: owner decision O07)");
        }
        return new S3ObjectStorage(storage);
    }

    @Bean
    MalwareScanner malwareScanner(MatbakhyProperties platform) {
        DeploymentEnvironment env = platform.environment();
        if (env.synthetic()) {
            return new FakeMalwareScanner();
        }
        throw new IllegalStateException("No malware scanner is configured for " + env + "; uploads cannot be approved (O07)");
    }

    @Bean
    EmailSender emailSender(MatbakhyProperties platform) {
        requireLocal(platform, "email");
        return LoggingNotificationChannels.email();
    }

    @Bean
    PushSender pushSender(MatbakhyProperties platform, NotificationProperties notifications) {
        DeploymentEnvironment env = platform.environment();
        boolean local = env.developmentSecrets();
        if (!local && (notifications.tokenHashKey().startsWith(NotificationProperties.DEV_PREFIX)
                || notifications.tokenEncryptionKey().startsWith(NotificationProperties.DEV_PREFIX))) {
            throw new IllegalStateException("PUSH_TOKEN_HASH_KEY and PUSH_TOKEN_ENCRYPTION_KEY must be set for " + env);
        }
        requireLocal(platform, "push");
        return LoggingNotificationChannels.push();
    }

    /** Deployed environments need real providers (owner decision O07) and real token keys. */
    private static void requireLocal(MatbakhyProperties platform, String channel) {
        DeploymentEnvironment env = platform.environment();
        if (!env.synthetic()) {
            throw new IllegalStateException("No " + channel + " notification provider is configured for " + env + " (O07)");
        }
    }
}
