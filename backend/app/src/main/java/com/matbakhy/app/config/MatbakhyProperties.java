package com.matbakhy.app.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Validated deployment configuration. Startup fails when a required value is missing or invalid. */
@Validated
@ConfigurationProperties("matbakhy")
public record MatbakhyProperties(@NotNull DeploymentEnvironment environment, @NotNull Market market) {

    /**
     * PREVIEW is an access-restricted web preview with synthetic data (owner direction 2026-10-12): synthetic providers
     * and fixtures as in LOCAL/CI, but real secrets, real S3-compatible storage, HTTPS cookies and a confidential web
     * client, as in a deployed environment. It is never a release environment; STAGING and PRODUCTION are unchanged.
     */
    public enum DeploymentEnvironment {
        LOCAL, CI, PREVIEW, STAGING, PRODUCTION;

        /** Synthetic fixtures, synthetic commerce policy and providers that never send anything. */
        public boolean synthetic() {
            return this == LOCAL || this == CI || this == PREVIEW;
        }

        /** Development default secrets and the local storage emulator are acceptable. */
        public boolean developmentSecrets() {
            return this == LOCAL || this == CI;
        }

        /** Served to people over HTTPS: secure cookies and a confidential web client are required. */
        public boolean deployed() {
            return this == PREVIEW || this == STAGING || this == PRODUCTION;
        }
    }

    public record Market(@NotNull @Pattern(regexp = "EGP") String currency,
                         @NotNull @Pattern(regexp = "Africa/Cairo") String timezone) {
    }
}
