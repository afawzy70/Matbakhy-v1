package com.matbakhy.app.config;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MatbakhyProperties.class)
class PlatformConfiguration {

    /** UTC system clock; tests substitute a fixed clock. Business scheduling converts with the market zone. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    EnvironmentGuardBean environmentGuard(MatbakhyProperties properties, Environment environment) {
        EnvironmentGuard.check(properties.environment(), environment);
        return new EnvironmentGuardBean();
    }

    /** Marker bean so the guard runs during context refresh, before the application accepts traffic. */
    static final class EnvironmentGuardBean {
    }
}
