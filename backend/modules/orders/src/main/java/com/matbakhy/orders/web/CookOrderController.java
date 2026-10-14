package com.matbakhy.orders.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.orders.internal.CookDashboardService;
import com.matbakhy.orders.internal.CookDashboardService.CookDashboard;
import com.matbakhy.orders.internal.OrderLifecycleService;
import com.matbakhy.orders.internal.OrderLifecycleService.Command;
import com.matbakhy.orders.internal.OrderService;
import com.matbakhy.orders.internal.OrderService.CookOrderPage;
import com.matbakhy.orders.internal.OrderService.CookOrderView;
import com.matbakhy.shared.web.Languages;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-045 and API-053..062: the cook's own kitchen orders and lifecycle commands (Idempotency-Key and expectedVersion). */
@RestController
class CookOrderController {

    private final OrderService orders;
    private final OrderLifecycleService lifecycle;
    private final CookDashboardService dashboard;

    CookOrderController(OrderService orders, OrderLifecycleService lifecycle, CookDashboardService dashboard) {
        this.orders = orders;
        this.lifecycle = lifecycle;
        this.dashboard = dashboard;
    }

    /** API-045: own queue counts, acceptance deadlines, upcoming slot totals and kitchen settings. */
    @GetMapping("/api/v1/cook/dashboard")
    CookDashboard dashboard(@AuthenticationPrincipal CurrentAccount actor) {
        return dashboard.dashboard(actor.id());
    }

    record CommandBody(@NotNull Long expectedVersion, @Size(max = 64) String reasonCode, @Size(max = 500) String note,
                       @Size(max = 200) String deliveryReference, @Size(max = 6) String code) {
    }

    @GetMapping("/api/v1/cook/orders")
    CookOrderPage list(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(required = false) String state,
                       @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit,
                       @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return orders.cookOrders(actor.id(), state, cursor, limit, Languages.prefersEnglish(acceptLanguage));
    }

    @GetMapping("/api/v1/cook/orders/{orderId}")
    CookOrderView get(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId,
                      @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return orders.cookOrder(actor.id(), orderId, Languages.prefersEnglish(acceptLanguage));
    }

    @PostMapping({"/api/v1/cook/orders/{orderId}/accept", "/api/v1/cook/orders/{orderId}/reject", "/api/v1/cook/orders/{orderId}/prepare",
        "/api/v1/cook/orders/{orderId}/ready", "/api/v1/cook/orders/{orderId}/dispatch", "/api/v1/cook/orders/{orderId}/handover",
        "/api/v1/cook/orders/{orderId}/delivery-failure", "/api/v1/cook/orders/{orderId}/cancel"})
    CookOrderView command(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId, HttpServletRequest request,
                          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                          @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
                          @Valid @RequestBody CommandBody body) {
        String uri = request.getRequestURI();
        String action = uri.substring(uri.lastIndexOf('/') + 1).replace('-', '_');
        lifecycle.cook(actor.id(), idempotencyKey, new Command(orderId, action, body.expectedVersion(), body.reasonCode(),
                body.note(), body.deliveryReference(), body.code()));
        return orders.cookOrder(actor.id(), orderId, Languages.prefersEnglish(acceptLanguage));
    }
}
