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
}
