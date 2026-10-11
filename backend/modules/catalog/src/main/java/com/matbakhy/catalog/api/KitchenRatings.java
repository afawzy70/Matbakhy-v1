package com.matbakhy.catalog.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Visible-review ratings per kitchen; implemented by the orders module, which owns reviews (BR-039). */
public interface KitchenRatings {

    /** count of visible reviews and the average in tenths (10..50), or null without reviews. */
    record Rating(int count, Integer averageTenths) {
    }

    Map<UUID, Rating> ratings(Collection<UUID> kitchenIds);
}
