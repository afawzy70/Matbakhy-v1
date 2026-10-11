package com.matbakhy.orders.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Support cases linked to orders; implemented by the support module so orders never reads its tables. */
public interface OrderCases {

    List<UUID> caseIds(UUID orderId);

    /** Orders with a case that is not RESOLVED or CLOSED. */
    Set<UUID> ordersWithOpenCases();
}
