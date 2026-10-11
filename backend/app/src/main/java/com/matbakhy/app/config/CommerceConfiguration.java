package com.matbakhy.app.config;

import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import com.matbakhy.orders.api.SyntheticPolicyPermission;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Synthetic commerce policies are usable only in LOCAL and CI; deployed environments need an approved policy. */
@Configuration(proxyBeanMethods = false)
class CommerceConfiguration {

    @Bean
    SyntheticPolicyPermission syntheticPolicyPermission(MatbakhyProperties platform) {
        boolean local = platform.environment() == DeploymentEnvironment.LOCAL || platform.environment() == DeploymentEnvironment.CI;
        return () -> local;
    }

    /** Deployed environments must not run with the development handover keys. */
    @Bean
    Object handoverKeyGuard(MatbakhyProperties platform, com.matbakhy.orders.internal.HandoverProperties handover) {
        boolean local = platform.environment() == DeploymentEnvironment.LOCAL || platform.environment() == DeploymentEnvironment.CI;
        if (!local && (handover.hashKey().startsWith(com.matbakhy.orders.internal.HandoverProperties.DEV_PREFIX)
                || handover.encryptionKey().startsWith(com.matbakhy.orders.internal.HandoverProperties.DEV_PREFIX))) {
            throw new IllegalStateException("HANDOVER_HASH_KEY and HANDOVER_ENCRYPTION_KEY must be set for " + platform.environment());
        }
        return new Object();
    }
}
