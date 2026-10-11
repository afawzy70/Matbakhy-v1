package com.matbakhy.identity.internal;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** BR-004 defaults: 5-minute single-use codes, 5 attempts, 60 s resend, bounded hourly sends. */
@Validated
@ConfigurationProperties("matbakhy.phone-verification")
public record PhoneVerificationProperties(
        @NotNull @Size(min = 32) String codePepper,
        @NotNull Duration codeTtl,
        @Min(1) int maxAttempts,
        @NotNull Duration resendInterval,
        @Min(1) int maxSendsPerAccountPerHour,
        @Min(1) int maxSendsPerPhonePerHour) {
}
