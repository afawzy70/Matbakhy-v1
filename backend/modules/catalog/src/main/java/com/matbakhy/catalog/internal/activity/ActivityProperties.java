package com.matbakhy.catalog.internal.activity;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * MB-054 organic activity. The default hash key is accepted only in LOCAL/CI; deployed environments must set
 * ACTIVITY_HASH_KEY (guarded at startup). The per-visitor daily cap bounds what one visitor id can add in a day.
 */
@Validated
@ConfigurationProperties("matbakhy.activity")
public record ActivityProperties(@NotNull @Size(min = 32) String hashKey, @Min(1) int maxEventsPerVisitorDay) {

    public static final String DEV_PREFIX = "local-dev-only-";
}
