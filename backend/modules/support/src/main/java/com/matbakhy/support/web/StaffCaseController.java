package com.matbakhy.support.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.shared.web.RequestContext;
import com.matbakhy.support.internal.SupportService;
import com.matbakhy.support.internal.SupportService.CasePage;
import com.matbakhy.support.internal.SupportService.Message;
import com.matbakhy.support.internal.SupportService.StaffCase;
import com.matbakhy.support.internal.SupportService.StaffUpdate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-078..081: the support queue for staff with support.case; staff-only notes appear only here. */
@RestController
@PreAuthorize("hasAuthority('PERM_support.case')")
class StaffCaseController {

    private final SupportService support;

    StaffCaseController(SupportService support) {
        this.support = support;
    }

    record StaffMessageBody(@Size(max = 4000) String body, @NotNull String visibility, @Size(max = 0) List<UUID> attachmentIds) {
    }

    record UpdateBody(Boolean assignToMe, @Size(max = 40) String state, @Size(max = 40) String resolutionCode, @Size(max = 1000) String reason,
                      @NotNull Long expectedVersion) {
    }

    @GetMapping("/api/v1/ops/cases")
    CasePage<StaffCase> queue(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(required = false) String state,
                              @RequestParam(required = false) String category, @RequestParam(required = false) String severity,
                              @RequestParam(defaultValue = "false") boolean mine, @RequestParam(defaultValue = "false") boolean overdue,
                              @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return support.queue(actor.id(), state, category, severity, mine, overdue, cursor, limit);
    }

    @GetMapping("/api/v1/ops/cases/{caseId}")
    StaffCase get(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID caseId, HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return support.viewStaffCase(actor.id(), caseId, requestId == null ? null : requestId.toString());
    }

    @PostMapping("/api/v1/ops/cases/{caseId}/messages")
    @ResponseStatus(HttpStatus.CREATED)
    Message message(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID caseId,
                    @RequestHeader(name = "Idempotency-Key", required = false) String key, @Valid @RequestBody StaffMessageBody body,
                    HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return support.staffMessage(actor.id(), key, caseId, body.body(), body.visibility(), requestId == null ? null : requestId.toString());
    }

    @PatchMapping("/api/v1/ops/cases/{caseId}")
    StaffCase update(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID caseId, @Valid @RequestBody UpdateBody body,
                     HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return support.update(actor.id(), caseId, new StaffUpdate(body.assignToMe(), body.state(), body.resolutionCode(), body.reason(),
                body.expectedVersion()), requestId == null ? null : requestId.toString());
    }
}
