package com.matbakhy.orders.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.orders.internal.PaymentService;
import com.matbakhy.orders.internal.PaymentService.PaymentView;
import com.matbakhy.orders.internal.PaymentService.Decision;
import com.matbakhy.orders.internal.PaymentService.Problem;
import com.matbakhy.orders.internal.PaymentService.ProfileView;
import com.matbakhy.orders.internal.PaymentService.QueueItem;
import com.matbakhy.orders.internal.PaymentService.Recipient;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    // ---- MB-052 ----

    @GetMapping("/api/v1/cook/payment-profile")
    ProfileView profile(@AuthenticationPrincipal CurrentAccount actor) {
        return payments.profileOf(actor.id());
    }

    record RecipientBody(@NotBlank String method, @NotBlank @Size(min = 3, max = 64) String identifier, @NotBlank @Size(min = 2, max = 80) String name) {
    }

    record ProfileBody(@NotNull Long expectedVersion, @NotNull Integer depositBps, Long codMaxTotalMinor, @Valid RecipientBody recipient) {
    }

    @PutMapping("/api/v1/cook/payment-profile")
    ProfileView updateProfile(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody ProfileBody body) {
        RecipientBody r = body.recipient();
        return payments.updateProfile(actor.id(), body.expectedVersion(), body.depositBps(), body.codMaxTotalMinor(),
                r == null ? null : new Recipient(r.method(), r.identifier(), r.name()));
    }

    record ReasonedVersion(@NotNull Long expectedVersion, @NotBlank @Size(min = 10, max = 500) String reason) {
    }

    @PostMapping("/api/v1/ops/kitchens/{kitchenId}/payment-recipient/approval")
    @PreAuthorize("hasAuthority('PERM_finance.verify') and @staffAssurance.recentMfa(authentication)")
    ProfileView approve(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID kitchenId, @Valid @RequestBody ReasonedVersion body) {
        return payments.approveRecipient(actor.id(), kitchenId, body.expectedVersion(), body.reason());
    }

    record ProblemBody(@NotNull Long expectedVersion, @NotNull Problem kind, Long amountMinor, @Size(min = 1, max = 64) String reference) {
    }

    @PostMapping("/api/v1/cook/orders/{orderId}/payment/problem")
    PaymentView problem(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId, @Valid @RequestBody ProblemBody body) {
        return payments.reportProblem(actor.id(), orderId, body.expectedVersion(), body.kind(), body.amountMinor(), body.reference());
    }

    record Queue(List<QueueItem> items) {
    }

    @GetMapping("/api/v1/ops/payments")
    @PreAuthorize("hasAuthority('PERM_finance.verify')")
    Queue queue(@RequestParam String state) {
        return new Queue(payments.queue(state));
    }

    record DecisionBody(@NotNull Long expectedVersion, @NotNull Decision outcome, @NotBlank @Size(min = 10, max = 500) String reason) {
    }

    @PostMapping("/api/v1/ops/orders/{orderId}/payment/decision")
    @PreAuthorize("hasAuthority('PERM_finance.verify') and @staffAssurance.recentMfa(authentication)")
    PaymentView decide(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId, @Valid @RequestBody DecisionBody body) {
        return payments.decide(actor.id(), orderId, body.expectedVersion(), body.outcome(), body.reason());
    }

    record RefundBody(@NotNull Long expectedVersion, @NotBlank @Size(min = 1, max = 64) String reference) {
    }

    @PostMapping("/api/v1/cook/orders/{orderId}/payment/refund")
    PaymentView refund(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId, @Valid @RequestBody RefundBody body) {
        return payments.reportRefund(actor.id(), orderId, body.expectedVersion(), body.reference());
    }

    record Pending(List<PaymentService.PendingRecipient> items) {
    }

    @GetMapping("/api/v1/ops/payment-recipients")
    @PreAuthorize("hasAuthority('PERM_finance.verify')")
    Pending pending() {
        return new Pending(payments.pendingRecipients());
    }
}
