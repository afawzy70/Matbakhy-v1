package com.matbakhy.notifications.api;

import java.util.Map;
import java.util.UUID;

/**
 * Channel provider ports (ADR-004). Each send carries a stable delivery id that providers must use as their
 * idempotency key, so a retry after a crash between "sent" and "recorded" does not produce a second effective message.
 * A thrown exception is a transient failure (retried); {@link Result#permanent()} failures are not retried.
 */
public final class NotificationChannels {

    private NotificationChannels() {
    }

    public record Result(boolean delivered, boolean permanent, String providerRef, String error) {

        public static Result ok(String providerRef) {
            return new Result(true, false, providerRef, null);
        }

        public static Result permanentFailure(String error) {
            return new Result(false, true, null, error);
        }
    }

    /** Email to the account's verified address, resolved by the provider adapter (no address is stored here). */
    public interface EmailSender {
        Result send(UUID deliveryId, UUID accountId, String locale, String template, Map<String, String> params);
    }

    /** Push to one installation; payload carries only an event hint, never private details. */
    public interface PushSender {
        Result send(UUID deliveryId, String platform, String token, String template, Map<String, String> params);
    }
}
