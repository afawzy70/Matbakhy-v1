package com.matbakhy.support.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.support.internal.SupportService;
import com.matbakhy.support.internal.SupportService.CasePage;
import com.matbakhy.support.internal.SupportService.Message;
import com.matbakhy.support.internal.SupportService.SupportCase;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-028 and API-035..038: the customer's own cases (public messages only) and cancellation requests. */
@RestController
class SupportController {

    private final SupportService support;

    SupportController(SupportService support) {
        this.support = support;
    }

    record CaseBody(@NotNull UUID orderId, @NotNull @Size(max = 40) String category, @Size(max = 4000) String description,
                    @Size(max = 0) List<UUID> attachmentIds) {
    }

    record MessageBody(@Size(max = 4000) String body, @Size(max = 0) List<UUID> attachmentIds) {
    }

    record CancellationBody(@NotNull Long expectedVersion, @Size(max = 40) String reasonCode, @Size(max = 3900) String note) {
    }

    @PostMapping("/api/v1/support/cases")
    @ResponseStatus(HttpStatus.CREATED)
    SupportCase open(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody CaseBody body) {
        return support.open(actor.id(), body.orderId(), body.category(), body.description());
    }

    @GetMapping("/api/v1/support/cases")
    CasePage<SupportCase> list(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(required = false) String cursor,
                               @RequestParam(defaultValue = "20") int limit) {
        return support.ownCases(actor.id(), cursor, limit);
    }

    @GetMapping("/api/v1/support/cases/{caseId}")
    SupportCase get(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID caseId) {
        return support.customerCase(actor.id(), caseId);
    }

    @PostMapping("/api/v1/support/cases/{caseId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    Message message(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID caseId,
                    @RequestHeader(name = "Idempotency-Key", required = false) String key, @Valid @RequestBody MessageBody body) {
        return support.customerMessage(actor.id(), key, caseId, body.body());
    }

    @PostMapping("/api/v1/orders/{orderId}/cancellation-requests")
    @ResponseStatus(HttpStatus.CREATED)
    SupportCase requestCancellation(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId,
                                    @RequestHeader(name = "Idempotency-Key", required = false) String key, @Valid @RequestBody CancellationBody body) {
        return support.requestCancellation(actor.id(), key, orderId, body.expectedVersion(), body.reasonCode(), body.note());
    }
}
