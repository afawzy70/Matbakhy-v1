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

    @Test
    void preview_may_use_a_synthetic_policy_but_never_the_development_handover_keys() {
        assertThat(allowed(DeploymentEnvironment.PREVIEW)).isTrue();
        MatbakhyProperties preview = new MatbakhyProperties(DeploymentEnvironment.PREVIEW, new Market("EGP", "Africa/Cairo"));
        com.matbakhy.orders.internal.HandoverProperties dev = new com.matbakhy.orders.internal.HandoverProperties(
                "local-dev-only-handover-hash-key-not-secret", "local-dev-only-handover-encryption-key-not-secret", 5,
                java.time.Duration.ofHours(2), 30);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CommerceConfiguration().handoverKeyGuard(preview, dev))
                .hasMessageContaining("HANDOVER_HASH_KEY");
    }
}
