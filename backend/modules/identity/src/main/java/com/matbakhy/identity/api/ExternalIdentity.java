package com.matbakhy.identity.api;

import java.util.Objects;

/**
 * Identity asserted by the OIDC provider after signature/issuer/audience validation. Only the
 * provider subject is trusted for identity; role or permission claims are never read from it.
 */
public record ExternalIdentity(String issuer, String subject, boolean emailVerified, String displayName, String locale) {

    public ExternalIdentity {
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(subject, "subject");
    }
}
