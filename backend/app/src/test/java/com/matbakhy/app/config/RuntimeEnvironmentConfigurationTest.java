package com.matbakhy.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import org.junit.jupiter.api.Test;

class RuntimeEnvironmentConfigurationTest {

    @Test
    void every_environment_except_production_is_labelled_as_a_demo_or_test_environment() {
        for (DeploymentEnvironment e : DeploymentEnvironment.values()) {
            var published = RuntimeEnvironmentConfiguration.of(e);
            assertThat(published.name()).isEqualTo(e.name());
            assertThat(published.demo()).as(e.name()).isEqualTo(e != DeploymentEnvironment.PRODUCTION);
        }
    }
}
