package com.matbakhy.app.config;

import com.matbakhy.orders.api.SyntheticPolicyPermission;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Synthetic commerce policies are usable only in LOCAL, CI and PREVIEW; staging and production need an approved policy. */
@Configuration(proxyBeanMethods = false)
class CommerceConfiguration {

    @Bean
    SyntheticPolicyPermission syntheticPolicyPermission(MatbakhyProperties platform) {
        boolean synthetic = platform.environment().synthetic();
        return () -> synthetic;
    }

    /** Deployed environments must not run with the development handover keys. */
    @Bean
    Object handoverKeyGuard(MatbakhyProperties platform, com.matbakhy.orders.internal.HandoverProperties handover) {
        boolean local = platform.environment().developmentSecrets();
        if (!local && (handover.hashKey().startsWith(com.matbakhy.orders.internal.HandoverProperties.DEV_PREFIX)
                || handover.encryptionKey().startsWith(com.matbakhy.orders.internal.HandoverProperties.DEV_PREFIX))) {
            throw new IllegalStateException("HANDOVER_HASH_KEY and HANDOVER_ENCRYPTION_KEY must be set for " + platform.environment());
        }
        return new Object();
    }
}
