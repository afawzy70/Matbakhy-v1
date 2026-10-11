package com.matbakhy.app.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Validated deployment configuration. Startup fails when a required value is missing or invalid. */
@Validated
@ConfigurationProperties("matbakhy")
public record MatbakhyProperties(@NotNull DeploymentEnvironment environment, @NotNull Market market) {

    public enum DeploymentEnvironment { LOCAL, CI, STAGING, PRODUCTION }

    public record Market(@NotNull @Pattern(regexp = "EGP") String currency,
                         @NotNull @Pattern(regexp = "Africa/Cairo") String timezone) {
    }
}
