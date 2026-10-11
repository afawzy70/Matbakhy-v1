package com.matbakhy.finance.web;

import com.matbakhy.finance.internal.CashService;
import com.matbakhy.finance.internal.CashService.CashPage;
import com.matbakhy.finance.internal.CashService.CashReportView;
import com.matbakhy.finance.internal.CashService.Decision;
import com.matbakhy.finance.internal.CashService.ReportInput;
import com.matbakhy.finance.internal.CashService.VerificationInput;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-063 (cook cash report), API-082 (finance cash list) and API-083 (finance verification). */
@RestController
class CashController {

    private final CashService cash;

    CashController(CashService cash) {
        this.cash = cash;
    }

    record ReportBody(@NotNull @Min(0) Long amountMinor, @NotBlank String currency, @NotNull Instant collectedAt, @NotBlank String method,
                      @Size(max = 128) String externalReference, @NotNull @Min(0) Long expectedVersion) {
    }

    record VerificationBody(@NotNull Decision decision, @NotNull @Min(0) Long verifiedAmountMinor, @Size(max = 100) String reference,
                            @Size(max = 1000) String reason, @NotNull @Min(0) Long expectedVersion) {
    }

    private static String requestId(HttpServletRequest request) {
        Object id = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return id == null ? null : id.toString();
    }

    @PostMapping("/api/v1/cook/orders/{orderId}/cash-reports")
    @ResponseStatus(HttpStatus.CREATED)
    CashReportView report(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId,
                          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey, @Valid @RequestBody ReportBody body) {
        return cash.report(actor.id(), idempotencyKey, orderId, new ReportInput(body.amountMinor(), body.currency(), body.collectedAt(),
                body.method(), body.externalReference(), body.expectedVersion()));
    }

    @GetMapping("/api/v1/ops/finance/cash-reports")
    @PreAuthorize("hasAuthority('PERM_finance.read') or hasAuthority('PERM_finance.verify')")
    CashPage list(@RequestParam(required = false) String state, @RequestParam(required = false) UUID kitchenId,
                  @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return cash.list(state, kitchenId, cursor, limit);
    }

    @PostMapping("/api/v1/ops/finance/cash-reports/{reportId}/verification")
    @PreAuthorize("hasAuthority('PERM_finance.verify')")
    CashReportView verify(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID reportId,
                          @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                          @Valid @RequestBody VerificationBody body, HttpServletRequest request) {
        cash.refuseSelfVerification(actor.id(), reportId, requestId(request));
        return cash.verify(actor.id(), idempotencyKey, new VerificationInput(reportId, body.decision(), body.verifiedAmountMinor(), body.reference(),
                body.reason(), body.expectedVersion()), requestId(request));
    }
}
