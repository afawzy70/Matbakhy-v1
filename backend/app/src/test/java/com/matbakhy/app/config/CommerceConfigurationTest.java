package com.matbakhy.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.config.MatbakhyProperties.DeploymentEnvironment;
import com.matbakhy.app.config.MatbakhyProperties.Market;
import org.junit.jupiter.api.Test;

/** Synthetic commerce policies are permitted only in LOCAL and CI (BR-042). */
class CommerceConfigurationTest {

    private boolean allowed(DeploymentEnvironment env) {
        return new CommerceConfiguration().syntheticPolicyPermission(new MatbakhyProperties(env, new Market("EGP", "Africa/Cairo"))).allowed();
    }

    @Test
    void synthetic_policies_only_in_local_and_ci() {
        assertThat(allowed(DeploymentEnvironment.LOCAL)).isTrue();
        assertThat(allowed(DeploymentEnvironment.CI)).isTrue();
        assertThat(allowed(DeploymentEnvironment.STAGING)).isFalse();
        assertThat(allowed(DeploymentEnvironment.PRODUCTION)).isFalse();
    }
}
