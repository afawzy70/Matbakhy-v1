package com.matbakhy.orders.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Handover challenge settings (BR-031; proposed defaults to review before pilot). The default keys are accepted only
 * in LOCAL/CI; deployed environments must set HANDOVER_HASH_KEY and HANDOVER_ENCRYPTION_KEY (base64, 32 bytes).
 */
@Validated
@ConfigurationProperties("matbakhy.handover")
public record HandoverProperties(@NotNull @Size(min = 32) String hashKey, @NotNull @Size(min = 40) String encryptionKey,
                                 @Min(1) int maxAttempts, @NotNull Duration expiryAfterSlotEnd, @Min(1) int maxAttemptsPerActorPerHour) {

    public static final String DEV_PREFIX = "local-dev-only-";
}
