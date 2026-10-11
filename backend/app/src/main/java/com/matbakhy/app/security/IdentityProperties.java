package com.matbakhy.app.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * OIDC and web-session settings. Endpoints follow Keycloak's path layout under the issuer (the local
 * reference provider, D08); the production provider is an open decision (O07).
 */
@Validated
@ConfigurationProperties("matbakhy.identity")
public record IdentityProperties(
        @NotBlank String issuer,
        String backchannelIssuer,
        @NotBlank String apiAudience,
        @NotBlank String webClientId,
        String webClientSecret,
        @NotEmpty List<String> allowedOrigins,
        @NotBlank String postLoginRedirect,
        @NotEmpty List<String> mfaAcrValues,
        Duration stepUpMaxAge,
        String bootstrapAdminSubject) {

    public IdentityProperties {
        if (backchannelIssuer == null || backchannelIssuer.isBlank()) {
            backchannelIssuer = issuer;
        }
        if (stepUpMaxAge == null) {
            stepUpMaxAge = Duration.ofMinutes(10);
        }
    }

    /** Browser-facing endpoint (authorization, end-session). */
    String endpoint(String path) {
        return issuer + "/protocol/openid-connect/" + path;
    }

    /** Server-to-provider endpoint (token, JWKS, userinfo); differs from the issuer inside container networks. */
    String backchannel(String path) {
        return backchannelIssuer + "/protocol/openid-connect/" + path;
    }
}
