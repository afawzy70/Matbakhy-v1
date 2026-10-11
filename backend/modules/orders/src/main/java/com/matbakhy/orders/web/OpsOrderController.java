package com.matbakhy.orders.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.orders.internal.OpsOrderService;
import com.matbakhy.orders.internal.OpsOrderService.OpsOrderPage;
import com.matbakhy.orders.internal.OpsOrderService.OpsOrderView;
import com.matbakhy.orders.internal.OrderLifecycleService;
import com.matbakhy.orders.internal.OrderLifecycleService.Command;
import com.matbakhy.orders.internal.OrderService;
import com.matbakhy.orders.internal.OrderService.OrderView;
import com.matbakhy.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-074/075 order queue and view (order.ops); API-076/077 audited exceptions. The handover override also needs recent MFA. */
@RestController
class OpsOrderController {

    private final OrderLifecycleService lifecycle;
    private final OrderService orders;
    private final OpsOrderService queue;

    OpsOrderController(OrderLifecycleService lifecycle, OrderService orders, OpsOrderService queue) {
        this.lifecycle = lifecycle;
        this.orders = orders;
        this.queue = queue;
    }

    @GetMapping("/api/v1/ops/orders")
    @PreAuthorize("hasAuthority('PERM_order.ops')")
    OpsOrderPage list(@RequestParam(required = false) String state, @RequestParam(required = false) UUID kitchenId,
                      @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                      @RequestParam(required = false) String exception, @RequestParam(required = false) String cursor,
                      @RequestParam(defaultValue = "20") int limit) {
        return queue.list(state, kitchenId, from, to, exception, cursor, limit);
    }

    @GetMapping("/api/v1/ops/orders/{orderId}")
    @PreAuthorize("hasAuthority('PERM_order.ops')")
    OpsOrderView view(@PathVariable UUID orderId) {
        return queue.view(orderId);
    }

    record ExceptionCancelBody(@NotNull Long expectedVersion, @NotBlank @Size(max = 40) String reasonCode, @NotBlank @Size(max = 100) String caseId,
                               @Size(max = 1000) String note) {
    }

    record OverrideBody(@NotNull Long expectedVersion, @NotBlank @Size(max = 1000) String reason, @NotBlank @Size(max = 200) String evidenceRef) {
    }

    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return id == null ? null : id.toString();
    }

    @PostMapping("/api/v1/ops/orders/{orderId}/exception-cancel")
    @PreAuthorize("hasAuthority('PERM_order.exception_cancel')")
    OrderView exceptionCancel(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId,
                                        @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                        @Valid @RequestBody ExceptionCancelBody body, HttpServletRequest request) {
        UUID id = lifecycle.opsExceptionCancel(actor.id(), idempotencyKey,
                new Command(orderId, "exception_cancel", body.expectedVersion(), body.reasonCode(), body.note(), body.caseId(), null), requestId(request));
        return orders.staffView(id);
    }

    @PostMapping("/api/v1/ops/orders/{orderId}/handover-override")
    @PreAuthorize("hasAuthority('PERM_order.handover_override') and @staffAssurance.recentMfa(authentication)")
    OrderView handoverOverride(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId,
                                         @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                         @Valid @RequestBody OverrideBody body, HttpServletRequest request) {
        UUID id = lifecycle.opsHandoverOverride(actor.id(), idempotencyKey,
                new Command(orderId, "handover_override", body.expectedVersion(), null, body.reason(), body.evidenceRef(), null), requestId(request));
        return orders.staffView(id);
    }
}
