package com.matbakhy.catalog.internal.media;

import com.matbakhy.catalog.api.MediaLibrary.Purpose;
import java.util.Set;

/** Per-purpose allowlists and limits (BR-010). Values are proposed defaults pending operational review. */
final class MediaPolicy {

    record Limits(Set<String> contentTypes, long maxBytes, int minSide, int maxSide, long maxPixels, boolean publicVariant) {
    }

    static final int PUBLIC_MAX_SIDE = 2048;

    private MediaPolicy() {
    }

    static Limits of(Purpose purpose) {
        return switch (purpose) {
            case MEAL_PHOTO -> new Limits(Set.of(MediaInspector.JPEG, MediaInspector.PNG), 8L * 1024 * 1024, 320, 8000, 40_000_000L, true);
            case COOK_DOCUMENT -> new Limits(Set.of(MediaInspector.JPEG, MediaInspector.PNG, MediaInspector.PDF), 10L * 1024 * 1024, 200,
                    10_000, 50_000_000L, false);
        };
    }
}
