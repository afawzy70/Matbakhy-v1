package com.matbakhy.archfixture.finance.api;

import com.matbakhy.archfixture.orders.api.OrderSummary;

public record LedgerView(OrderSummary order) {
}
