package com.matbakhy.identity.api;

/**
 * Port for delivering a one-time contact code (ADR-004). Implementations must never log the code.
 * Only a fake exists today; staging and production need an approved provider (O07).
 */
public interface PhoneVerificationSender {

    void send(String phoneE164, String code, String locale);
}
