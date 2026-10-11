package com.matbakhy.archfixture.finance.internal;

import com.matbakhy.archfixture.orders.api.OrderSummary;
import com.matbakhy.archfixture.orders.internal.Secret;

/** Violation: reaches into another module's internal package. */
public class Leak {
    public int read(Secret secret, OrderSummary allowed) {
        return secret.value() + (allowed == null ? 0 : 1);
    }
}
