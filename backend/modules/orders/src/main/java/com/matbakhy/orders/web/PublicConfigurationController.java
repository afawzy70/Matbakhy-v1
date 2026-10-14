package com.matbakhy.orders.web;

import com.matbakhy.orders.internal.CommercePolicies;
import com.matbakhy.orders.internal.CommercePolicies.Policy;
import com.matbakhy.shared.config.RuntimeEnvironment;
import com.matbakhy.shared.error.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-001: public configuration for clients. It publishes the active commerce policy's per-line quantity limit so that
 * web and native apps bound their quantity controls by the server's value (Q1 option A) instead of a hard-coded one.
 * The quote stays the enforcement point; commission, tax and other policy internals are never exposed here. It also names
 * the environment and whether it is a demo/test one, so clients can say so on every screen (owner decision 2026-10-14).
 */
@RestController
class PublicConfigurationController {

    record OrderLimits(int maxQuantityPerLine, int maxLinesPerOrder, int policyVersion) {
    }

    record PublicConfiguration(boolean orderingAvailable, OrderLimits orderLimits, RuntimeEnvironment environment) {
    }

    private final CommercePolicies policies;
    private final RuntimeEnvironment environment;

    PublicConfigurationController(CommercePolicies policies, RuntimeEnvironment environment) {
        this.policies = policies;
        this.environment = environment;
    }

    @GetMapping("/api/v1/configuration/public")
    PublicConfiguration view() {
        try {
            Policy p = policies.active();
            return new PublicConfiguration(true, new OrderLimits(p.maxItemQuantity(), CommercePolicies.MAX_LINES_PER_ORDER, p.version()), environment);
        } catch (ApiException closed) {
            // Ordering paused or no usable policy: quotes would be refused, so no limits are offered.
            return new PublicConfiguration(false, null, environment);
        }
    }
}
