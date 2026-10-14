package com.matbakhy.catalog.api;

import java.util.UUID;

/** Media facts other modules may rely on when attaching uploads to their own records. */
public interface MediaLibrary {

    enum Purpose { MEAL_PHOTO, COOK_DOCUMENT }

    /** True only for media owned by {@code ownerId}, of this purpose, that passed every check (APPROVED). */
    boolean approvedFor(UUID ownerId, UUID mediaId, Purpose purpose);
}
