package com.matbakhy.app.providers;

import com.matbakhy.identity.api.PhoneVerificationSender;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Development/test fake: keeps the last code per number in memory so tests and local developers can
 * complete verification. Never sends anything and never logs the code. Refused outside LOCAL/CI.
 */
public final class FakePhoneVerificationSender implements PhoneVerificationSender {

    private final Map<String, String> lastCodes = new ConcurrentHashMap<>();

    @Override
    public void send(String phoneE164, String code, String locale) {
        lastCodes.put(phoneE164, code);
    }

    public Optional<String> lastCode(String phoneE164) {
        return Optional.ofNullable(lastCodes.get(phoneE164));
    }
}
