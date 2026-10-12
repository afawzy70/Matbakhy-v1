package com.matbakhy.app.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import java.util.List;
import org.junit.jupiter.api.Test;

class EnvironmentGuardTest {

    private static final String WITH_FIXTURES = "classpath:db/migration,classpath:db/devdata";

    @Test
    void fixtures_allowed_locally_and_in_ci() {
        assertThatCode(() -> EnvironmentGuard.check(DeploymentEnvironment.LOCAL, List.of("dev"), WITH_FIXTURES))
                .doesNotThrowAnyException();
        assertThatCode(() -> EnvironmentGuard.check(DeploymentEnvironment.CI, List.of("dev"), WITH_FIXTURES))
                .doesNotThrowAnyException();
    }

    @Test
    void production_and_staging_reject_dev_profile_and_fixture_migrations() {
        for (DeploymentEnvironment env : List.of(DeploymentEnvironment.STAGING, DeploymentEnvironment.PRODUCTION)) {
            assertThatThrownBy(() -> EnvironmentGuard.check(env, List.of("dev"), "classpath:db/migration"))
                    .isInstanceOf(IllegalStateException.class);
            assertThatThrownBy(() -> EnvironmentGuard.check(env, List.of(), WITH_FIXTURES))
                    .isInstanceOf(IllegalStateException.class);
            assertThatCode(() -> EnvironmentGuard.check(env, List.of(), "classpath:db/migration"))
                    .doesNotThrowAnyException();
        }
    }

    /** PREVIEW: an access-restricted, synthetic-data web preview (owner direction 2026-10-12); never a release environment. */
    @Test
    void preview_allows_the_synthetic_fixtures() {
        assertThatCode(() -> EnvironmentGuard.check(DeploymentEnvironment.PREVIEW, List.of("dev"), WITH_FIXTURES)).doesNotThrowAnyException();
    }
}
