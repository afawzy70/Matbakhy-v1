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

    @Test
    void deployed_environments_refuse_the_development_activity_key() {
        com.matbakhy.catalog.internal.activity.ActivityProperties dev = new com.matbakhy.catalog.internal.activity.ActivityProperties(
                "local-dev-only-activity-hash-key-not-a-secret", 300);
        for (DeploymentEnvironment env : new DeploymentEnvironment[] {DeploymentEnvironment.PREVIEW, DeploymentEnvironment.STAGING,
                DeploymentEnvironment.PRODUCTION}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> new CommerceConfiguration().activityKeyGuard(
                    new MatbakhyProperties(env, new Market("EGP", "Africa/Cairo")), dev)).hasMessageContaining("ACTIVITY_HASH_KEY");
        }
        assertThat(new CommerceConfiguration().activityKeyGuard(new MatbakhyProperties(DeploymentEnvironment.CI, new Market("EGP", "Africa/Cairo")), dev))
                .isNotNull();
        com.matbakhy.catalog.internal.activity.ActivityProperties real = new com.matbakhy.catalog.internal.activity.ActivityProperties(
                "0123456789abcdef0123456789abcdef0123456789abcdef", 300);
        assertThat(new CommerceConfiguration().activityKeyGuard(new MatbakhyProperties(DeploymentEnvironment.PRODUCTION,
                new Market("EGP", "Africa/Cairo")), real)).isNotNull();
    }
}
