package com.matbakhy.cooks.api;

import java.util.Map;
import java.util.UUID;

/**
 * Active orders of a kitchen per state, for the operations impact summary when pausing, suspending or restoring it.
 * Implemented by the orders module (which depends on cooks), so cooks never reads order tables.
 */
public interface KitchenOrderImpact {

    Map<String, Integer> activeOrders(UUID kitchenId);
}
