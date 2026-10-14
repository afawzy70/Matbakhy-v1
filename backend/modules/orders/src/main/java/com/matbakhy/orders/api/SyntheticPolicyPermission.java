package com.matbakhy.orders.api;

/**
 * Supplied by the application for the deployment environment: synthetic (placeholder) commerce policies may be
 * used only where this returns true (LOCAL/CI). Deployed environments need an approved real policy (BR-042, O04).
 */
public interface SyntheticPolicyPermission {

    boolean allowed();
}
