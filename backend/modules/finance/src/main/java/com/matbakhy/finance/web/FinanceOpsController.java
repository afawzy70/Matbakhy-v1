package com.matbakhy.finance.web;

import com.matbakhy.finance.internal.FinanceOpsService;
import com.matbakhy.finance.internal.FinanceOpsService.Refund;
import com.matbakhy.finance.internal.FinanceOpsService.Settlement;
import com.matbakhy.finance.internal.StatementService;
import com.matbakhy.finance.internal.StatementService.StatementPage;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
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

/** API-084..088: refunds (finance.refund_approve), statements (finance.read) and settlements (finance.settlement). */
@RestController
class FinanceOpsController {

    private final FinanceOpsService finance;
    private final StatementService statements;

    FinanceOpsController(FinanceOpsService finance, StatementService statements) {
        this.finance = finance;
        this.statements = statements;
    }

    record RefundBody(@NotNull UUID orderId, @NotNull @Size(max = 100) String caseId, @NotNull Long fullAmountMinor, @Size(max = 1000) String reason) {
    }

    record ActionBody(@NotNull @Size(max = 40) String action, @Size(max = 200) String evidenceRef, @Size(max = 1000) String reason,
                      @NotNull Long expectedVersion) {
    }

    record Period(@NotNull LocalDate from, @NotNull LocalDate to) {
    }

    record SettlementBody(@NotNull UUID kitchenId, @NotNull @Valid Period period, @NotNull @Size(min = 1, max = 500) List<UUID> entryIds) {
    }

    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return id == null ? null : id.toString();
    }

    @PostMapping("/api/v1/ops/finance/refunds")
    @PreAuthorize("hasAuthority('PERM_finance.refund_approve')")
    Refund requestRefund(@AuthenticationPrincipal CurrentAccount actor, @RequestHeader(name = "Idempotency-Key", required = false) String key,
                         @Valid @RequestBody RefundBody body, HttpServletRequest request) {
        return finance.requestRefund(actor.id(), key, body.orderId(), body.caseId(), body.fullAmountMinor(), body.reason(), requestId(request));
    }

    @PostMapping("/api/v1/ops/finance/refunds/{refundId}/action")
    @PreAuthorize("hasAuthority('PERM_finance.refund_approve')")
    Refund refundAction(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID refundId,
                        @RequestHeader(name = "Idempotency-Key", required = false) String key, @Valid @RequestBody ActionBody body, HttpServletRequest request) {
        return finance.refundAction(actor.id(), key, refundId, body.action(), body.expectedVersion(), body.evidenceRef(), body.reason(), requestId(request));
    }

    @GetMapping("/api/v1/ops/finance/statements")
    @PreAuthorize("hasAuthority('PERM_finance.read') or hasAuthority('PERM_finance.settlement') or hasAuthority('PERM_finance.refund_approve')")
    StatementPage statement(@RequestParam UUID kitchenId, @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                            @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return statements.forKitchen(kitchenId, from, to, cursor, limit);
    }

    @PostMapping("/api/v1/ops/finance/settlements")
    @PreAuthorize("hasAuthority('PERM_finance.settlement')")
    Settlement createSettlement(@AuthenticationPrincipal CurrentAccount actor, @RequestHeader(name = "Idempotency-Key", required = false) String key,
                                @Valid @RequestBody SettlementBody body, HttpServletRequest request) {
        return finance.createSettlement(actor.id(), key, body.kitchenId(), body.period().from(), body.period().to(), body.entryIds(), requestId(request));
    }

    @PostMapping("/api/v1/ops/finance/settlements/{batchId}/action")
    @PreAuthorize("hasAuthority('PERM_finance.settlement')")
    Settlement settlementAction(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID batchId,
                                @RequestHeader(name = "Idempotency-Key", required = false) String key, @Valid @RequestBody ActionBody body,
                                HttpServletRequest request) {
        return finance.settlementAction(actor.id(), key, batchId, body.action(), body.expectedVersion(), body.evidenceRef(), requestId(request));
    }
}
