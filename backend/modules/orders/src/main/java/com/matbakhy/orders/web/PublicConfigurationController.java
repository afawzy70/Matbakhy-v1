package com.matbakhy.orders.web;

import com.matbakhy.orders.internal.CommercePolicies;
import com.matbakhy.orders.internal.CommercePolicies.Policy;
import com.matbakhy.shared.error.ApiException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API-001: public configuration for clients. It publishes the active commerce policy's per-line quantity limit so that
 * web and native apps bound their quantity controls by the server's value (Q1 option A) instead of a hard-coded one.
 * The quote stays the enforcement point; commission, tax and other policy internals are never exposed here.
 */
@RestController
class PublicConfigurationController {

    record OrderLimits(int maxQuantityPerLine, int maxLinesPerOrder, int policyVersion) {
    }

    record PublicConfiguration(boolean orderingAvailable, OrderLimits orderLimits) {
    }

    private final CommercePolicies policies;

    PublicConfigurationController(CommercePolicies policies) {
        this.policies = policies;
    }

    @GetMapping("/api/v1/configuration/public")
    PublicConfiguration view() {
        try {
            Policy p = policies.active();
            return new PublicConfiguration(true, new OrderLimits(p.maxItemQuantity(), CommercePolicies.MAX_LINES_PER_ORDER, p.version()));
        } catch (ApiException closed) {
            // Ordering paused or no usable policy: quotes would be refused, so no limits are offered.
            return new PublicConfiguration(false, null);
        }
    }
}
