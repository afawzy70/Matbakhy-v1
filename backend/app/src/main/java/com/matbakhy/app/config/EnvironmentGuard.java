package com.matbakhy.app.config;

import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import java.util.Arrays;
import java.util.List;
import org.springframework.core.env.Environment;

/**
 * Refuses to start staging or production with development fixtures: the {@code dev} profile or a
 * Flyway location containing {@code devdata} (synthetic seed data) is only legal for LOCAL and CI.
 */
public final class EnvironmentGuard {

    static final String DEV_PROFILE = "dev";
    static final String DEV_DATA_MARKER = "devdata";

    private EnvironmentGuard() {
    }

    public static void check(DeploymentEnvironment environment, Environment springEnvironment) {
        List<String> profiles = Arrays.asList(springEnvironment.getActiveProfiles());
        String locations = springEnvironment.getProperty("spring.flyway.locations", "");
        check(environment, profiles, locations);
    }

    static void check(DeploymentEnvironment environment, List<String> activeProfiles, String flywayLocations) {
        boolean fixtureCapable = environment.synthetic();
        if (fixtureCapable) {
            return;
        }
        if (activeProfiles.contains(DEV_PROFILE)) {
            throw new IllegalStateException("The dev profile is not allowed in " + environment);
        }
        if (flywayLocations.contains(DEV_DATA_MARKER)) {
            throw new IllegalStateException("Development fixture migrations are not allowed in " + environment);
        }
    }
}
