package com.matbakhy.app.config;

import com.matbakhy.app.web.RedactedThrowable;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.task.SimpleAsyncTaskSchedulerCustomizer;
import org.springframework.boot.task.ThreadPoolTaskSchedulerCustomizer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MatbakhyProperties.class)
@EnableScheduling
class PlatformConfiguration {

    private static final Logger log = LoggerFactory.getLogger(PlatformConfiguration.class);

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

    /** Background job failures are logged without exception messages, which may quote personal data (BR-056). */
    @Bean
    ThreadPoolTaskSchedulerCustomizer redactedSchedulerErrors() {
        return scheduler -> scheduler.setErrorHandler(PlatformConfiguration::logJobFailure);
    }

    @Bean
    SimpleAsyncTaskSchedulerCustomizer redactedSimpleSchedulerErrors() {
        return scheduler -> scheduler.setErrorHandler(PlatformConfiguration::logJobFailure);
    }

    private static void logJobFailure(Throwable failure) {
        log.error("Scheduled job failed", RedactedThrowable.of(failure));
    }

    /** Marker bean so the guard runs during context refresh, before the application accepts traffic. */
    static final class EnvironmentGuardBean {
    }
}
