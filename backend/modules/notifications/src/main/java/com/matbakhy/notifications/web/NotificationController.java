package com.matbakhy.notifications.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.notifications.internal.NotificationService;
import com.matbakhy.notifications.internal.NotificationService.DeviceRegistration;
import com.matbakhy.notifications.internal.NotificationService.IntegrationHealth;
import com.matbakhy.notifications.internal.NotificationService.Notification;
import com.matbakhy.notifications.internal.NotificationService.NotificationPage;
import com.matbakhy.notifications.internal.NotificationService.OutboxStatus;
import com.matbakhy.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-031..034 (own inbox and devices) and API-096/097 (integration operations). */
@RestController
class NotificationController {

    private final NotificationService notifications;

    NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    record DeviceBody(@NotBlank String platform, @NotBlank @Size(max = 4096) String pushToken, @NotBlank String environment,
                      @NotBlank @Size(max = 40) String appVersion) {
    }

    record RetryBody(@NotBlank @Size(max = 500) String reason, @NotNull Long expectedVersion) {
    }

    @GetMapping("/api/v1/notifications")
    NotificationPage inbox(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(defaultValue = "false") boolean unreadOnly,
                           @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return notifications.inbox(actor.id(), unreadOnly, cursor, limit);
    }

    @PutMapping("/api/v1/notifications/{notificationId}/read")
    Notification read(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID notificationId) {
        return notifications.markRead(actor.id(), notificationId);
    }

    @PutMapping("/api/v1/me/devices/{installationId}")
    DeviceRegistration register(@AuthenticationPrincipal CurrentAccount actor, @PathVariable String installationId, @Valid @RequestBody DeviceBody body) {
        return notifications.register(actor.id(), installationId, body.platform(), body.environment(), body.appVersion(), body.pushToken());
    }

    @DeleteMapping("/api/v1/me/devices/{installationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unregister(@AuthenticationPrincipal CurrentAccount actor, @PathVariable String installationId) {
        notifications.unregister(actor.id(), installationId);
    }

    @GetMapping("/api/v1/ops/integrations")
    @PreAuthorize("hasAuthority('PERM_integration.ops')")
    IntegrationHealth health() {
        return notifications.health();
    }

    @PostMapping("/api/v1/ops/outbox/{eventId}/retry")
    @PreAuthorize("hasAuthority('PERM_integration.ops')")
    OutboxStatus retry(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID eventId,
                       @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey, @Valid @RequestBody RetryBody body,
                       HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return notifications.retryOutbox(actor.id(), idempotencyKey, eventId, body.expectedVersion(), body.reason(),
                requestId == null ? null : requestId.toString());
    }
}
