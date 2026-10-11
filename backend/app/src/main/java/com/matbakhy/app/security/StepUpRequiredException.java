package com.matbakhy.app.security;

import org.springframework.security.access.AccessDeniedException;

/** The operation needs recent multi-factor authentication at the identity provider. */
public class StepUpRequiredException extends AccessDeniedException {

    public StepUpRequiredException() {
        super("Recent multi-factor authentication required");
    }
}
