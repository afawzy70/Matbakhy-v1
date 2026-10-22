package com.matbakhy.app.config;

import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import com.matbakhy.shared.config.RuntimeEnvironment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Publishes the deployment environment to the domain modules (API-001). Only production is not a demo/test environment. */
@Configuration(proxyBeanMethods = false)
class RuntimeEnvironmentConfiguration {

    @Bean
    RuntimeEnvironment runtimeEnvironment(MatbakhyProperties platform) {
        return of(platform.environment());
    }

    static RuntimeEnvironment of(DeploymentEnvironment environment) {
        return new RuntimeEnvironment(environment.name(), environment != DeploymentEnvironment.PRODUCTION);
    }
}
