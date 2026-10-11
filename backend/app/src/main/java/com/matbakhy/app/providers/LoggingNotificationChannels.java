package com.matbakhy.app.providers;

import com.matbakhy.notifications.api.NotificationChannels.EmailSender;
import com.matbakhy.notifications.api.NotificationChannels.PushSender;
import com.matbakhy.notifications.api.NotificationChannels.Result;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** LOCAL/CI stand-ins: accept every message and log only the delivery id and template, never recipients or tokens. */
final class LoggingNotificationChannels {

    private static final Logger LOG = LoggerFactory.getLogger(LoggingNotificationChannels.class);

    private LoggingNotificationChannels() {
    }

    static EmailSender email() {
        return (UUID deliveryId, UUID accountId, String locale, String template, Map<String, String> params) -> {
            LOG.info("fake email delivery {} template {}", deliveryId, template);
            return Result.ok("fake-email-" + deliveryId);
        };
    }

    static PushSender push() {
        return (UUID deliveryId, String platform, String token, String template, Map<String, String> params) -> {
            LOG.info("fake push delivery {} template {} platform {}", deliveryId, template, platform);
            return Result.ok("fake-push-" + deliveryId);
        };
    }
}
