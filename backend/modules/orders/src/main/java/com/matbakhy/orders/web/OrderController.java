package com.matbakhy.orders.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.orders.internal.OrderLifecycleService;
import com.matbakhy.orders.internal.OrderLifecycleService.Command;
import com.matbakhy.orders.internal.OrderService;
import com.matbakhy.orders.internal.OrderService.OrderEventPage;
import com.matbakhy.orders.internal.OrderService.OrderPage;
import com.matbakhy.orders.internal.OrderService.OrderView;
import com.matbakhy.orders.internal.OrderService.PlaceRequest;
import com.matbakhy.shared.web.Languages;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-022..026: placement and the customer's own orders (others' orders are 404). */
@RestController
class OrderController {

    private final OrderService orders;
    private final OrderLifecycleService lifecycle;

    OrderController(OrderService orders, OrderLifecycleService lifecycle) {
        this.orders = orders;
        this.lifecycle = lifecycle;
    }

    record CancelBody(@NotNull Long expectedVersion, @NotBlank @Size(max = 64) String reasonCode, @Size(max = 500) String note) {
    }

    /** API-027: pending orders only; afterwards 409 CANCELLATION_REQUEST_REQUIRED (requests arrive with support, MB-024). */
    @PostMapping("/api/v1/orders/{orderId}/cancel")
    OrderView cancel(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId,
                     @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                     @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
                     @Valid @RequestBody CancelBody body) {
        lifecycle.customerCancel(actor.id(), idempotencyKey, new Command(orderId, "cancel", body.expectedVersion(), body.reasonCode(), null, null, null));
        return orders.get(actor.id(), orderId, Languages.prefersEnglish(acceptLanguage));
    }

    record AcceptedPolicies(@NotBlank @Size(max = 40) String terms, @NotBlank @Size(max = 40) String cancellation) {
    }

    record PlaceBody(@NotNull UUID quoteId, @NotNull UUID clientRequestId, @NotNull @Valid AcceptedPolicies acceptedPolicyVersions,
                     @Size(max = 500) String fulfillmentNote) {
    }

    @PostMapping("/api/v1/orders")
    ResponseEntity<OrderView> place(@AuthenticationPrincipal CurrentAccount actor,
                                    @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                                    @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
                                    @Valid @RequestBody PlaceBody body) {
        OrderView order = orders.place(actor.id(), idempotencyKey, new PlaceRequest(body.quoteId(), body.clientRequestId(),
                body.acceptedPolicyVersions().terms(), body.acceptedPolicyVersions().cancellation(), body.fulfillmentNote()),
                Languages.prefersEnglish(acceptLanguage));
        return ResponseEntity.created(URI.create("/api/v1/orders/" + order.id())).body(order);
    }

    @GetMapping("/api/v1/orders")
    OrderPage list(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(required = false) String stateGroup,
                   @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit,
                   @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return orders.list(actor.id(), stateGroup, cursor, limit, Languages.prefersEnglish(acceptLanguage));
    }

    @GetMapping("/api/v1/orders/by-request/{clientRequestId}")
    OrderView byRequest(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID clientRequestId,
                        @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return orders.byRequest(actor.id(), clientRequestId, Languages.prefersEnglish(acceptLanguage));
    }

    @GetMapping("/api/v1/orders/{orderId}")
    OrderView get(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId,
                  @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return orders.get(actor.id(), orderId, Languages.prefersEnglish(acceptLanguage));
    }

    @GetMapping("/api/v1/orders/{orderId}/events")
    OrderEventPage events(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId,
                          @RequestParam(defaultValue = "0") int afterSequence, @RequestParam(defaultValue = "50") int limit) {
        return orders.events(actor.id(), orderId, afterSequence, limit);
    }
}
