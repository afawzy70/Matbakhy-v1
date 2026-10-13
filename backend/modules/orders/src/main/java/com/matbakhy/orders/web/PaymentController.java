package com.matbakhy.orders.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.orders.internal.PaymentService;
import com.matbakhy.orders.internal.PaymentService.PaymentView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * MB-051 (D16): the customer reports a sent deposit (API-100); the kitchen reports that it arrived in its own account
 * (API-101). Neither is a bank confirmation; no money passes through Matbakhy.
 */
@RestController
class PaymentController {

    private final PaymentService payments;

    PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    record TransferReport(@Size(min = 1, max = 64) String reference) {
    }

    @PostMapping("/api/v1/orders/{orderId}/payment/transfer-report")
    PaymentView report(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId, @Valid @RequestBody TransferReport body) {
        return payments.reportTransfer(actor.id(), orderId, body.reference());
    }

    record Receipt(@NotNull Long expectedVersion, @NotNull @Positive Long amountMinor, @Size(min = 1, max = 64) String reference) {
    }

    @PostMapping("/api/v1/cook/orders/{orderId}/payment/receipt")
    PaymentView receipt(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId, @Valid @RequestBody Receipt body) {
        return payments.kitchenReceipt(actor.id(), orderId, body.expectedVersion(), body.amountMinor(), body.reference());
    }
}
