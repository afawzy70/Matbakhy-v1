package com.matbakhy.archfixture.orders.api;

import com.matbakhy.archfixture.finance.api.LedgerView;

/** Allowed api-to-api use; combined with LedgerView it also forms a deliberate module cycle. */
public record OrderSummary(LedgerView ledger) {
}
