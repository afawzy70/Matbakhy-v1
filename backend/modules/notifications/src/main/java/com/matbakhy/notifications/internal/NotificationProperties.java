package com.matbakhy.notifications.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Retry/lease settings and the push-token keys (development defaults accepted only in LOCAL/CI). {@code pushEnabled} is
 * the O07 feature flag: off by default, so in-app notifications work and no push delivery is created until push is
 * approved, configured with credentials and tested on devices.
 */
@Validated
@ConfigurationProperties("matbakhy.notifications")
public record NotificationProperties(@NotNull @Size(min = 32) String tokenHashKey, @NotNull @Size(min = 32) String tokenEncryptionKey,
                                     @Min(1) int maxAttempts, @NotNull Duration initialBackoff, @NotNull Duration lease,
                                     boolean pushEnabled) {

    public static final String DEV_PREFIX = "local-dev-only-";
}
