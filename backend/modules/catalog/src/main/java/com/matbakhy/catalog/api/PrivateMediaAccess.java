package com.matbakhy.catalog.api;

import java.util.UUID;

/**
 * Implemented by modules that attach private media to their records (for example cook applications), so the
 * media module can grant scoped reviewer reads and refuse deleting media that a preserved record references.
 */
public interface PrivateMediaAccess {

    /** True when the media is part of a record the current staff permission set may review. */
    boolean reviewable(UUID mediaId);

    /** True when deleting the media would alter a preserved record (for example a submitted revision). */
    boolean referenced(UUID mediaId);
}
