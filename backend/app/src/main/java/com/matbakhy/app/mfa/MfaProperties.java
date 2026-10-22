package com.matbakhy.app.mfa;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Keycloak admin access used only for the audited MFA reset and for ending sessions after MFA is switched off. The
 * client is a service account limited to user credential/session management; the admin API is reachable on the
 * internal network only (the proxy never publishes /admin). Blank secret = the operations are unavailable.
 */
@ConfigurationProperties("matbakhy.mfa")
public record MfaProperties(String adminClientId, String adminClientSecret, String accountConsoleUrl) {

    public boolean adminConfigured() {
        return adminClientId != null && !adminClientId.isBlank() && adminClientSecret != null && !adminClientSecret.isBlank();
    }
}
