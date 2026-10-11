package com.matbakhy.app.providers;

import com.matbakhy.app.config.MatbakhyProperties;
import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import com.matbakhy.identity.api.PhoneVerificationSender;
import com.matbakhy.identity.internal.PhoneVerificationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provider adapters (ADR-004). Fakes exist only for LOCAL/CI; deployed environments fail fast without a real provider. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(PhoneVerificationProperties.class)
class ProviderConfiguration {

    static final String DEV_PEPPER_PREFIX = "local-dev-only-";

    @Bean
    PhoneVerificationSender phoneVerificationSender(MatbakhyProperties platform, PhoneVerificationProperties phone) {
        DeploymentEnvironment env = platform.environment();
        if (env == DeploymentEnvironment.LOCAL || env == DeploymentEnvironment.CI) {
            return new FakePhoneVerificationSender();
        }
        if (phone.codePepper().startsWith(DEV_PEPPER_PREFIX)) {
            throw new IllegalStateException("PHONE_CODE_PEPPER must be set to a secret value in " + env);
        }
        throw new IllegalStateException("No SMS verification provider is configured for " + env + " (owner decision O07)");
    }
}
