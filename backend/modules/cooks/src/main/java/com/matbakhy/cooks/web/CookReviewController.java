package com.matbakhy.cooks.web;

import com.matbakhy.cooks.internal.CookReviewService;
import com.matbakhy.cooks.internal.CookReviewService.ApplicationPage;
import com.matbakhy.cooks.internal.CookReviewService.ApplicationReview;
import com.matbakhy.cooks.internal.CookReviewService.Decision;
import com.matbakhy.cooks.internal.CookReviewService.DecisionRequest;
import com.matbakhy.cooks.internal.KitchenService;
import com.matbakhy.cooks.internal.KitchenService.Action;
import com.matbakhy.cooks.internal.KitchenService.KitchenAdmin;
import com.matbakhy.identity.api.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
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

/**
 * API-067..070. Reading needs cook.review; decisions and operating-state changes also need recent MFA
 * (high-risk staff actions, docs/product/06-permissions.md).
 */
@RestController
class CookReviewController {

    private final CookReviewService reviews;
    private final KitchenService kitchens;

    CookReviewController(CookReviewService reviews, KitchenService kitchens) {
        this.reviews = reviews;
        this.kitchens = kitchens;
    }

    record DecisionBody(@NotNull Decision decision, @Size(max = 2000) String reason, @NotNull @Min(1) Integer revision,
                        @NotNull @Min(0) Long expectedVersion) {
    }

    record OperatingStateBody(@NotNull Action action, @Size(max = 2000) String reason, @NotNull @Min(0) Long expectedVersion) {
    }

    @GetMapping("/api/v1/ops/cooks")
    @PreAuthorize("hasAuthority('PERM_cook.review')")
    ApplicationPage queue(@RequestParam(required = false) String state, @RequestParam(required = false) String q,
                          @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return reviews.queue(state, q, cursor, limit);
    }

    @GetMapping("/api/v1/ops/cooks/{applicationId}")
    @PreAuthorize("hasAuthority('PERM_cook.review')")
    ApplicationReview review(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID applicationId,
                             HttpServletRequest request) {
        return reviews.review(actor.id(), applicationId, Requests.id(request));
    }

    @PostMapping("/api/v1/ops/cooks/{applicationId}/decision")
    @PreAuthorize("hasAuthority('PERM_cook.review') and @staffAssurance.recentMfa(authentication)")
    ApplicationReview decide(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID applicationId,
                             @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
                             @Valid @RequestBody DecisionBody body, HttpServletRequest request) {
        String requestId = Requests.id(request);
        reviews.refuseSelfReview(actor.id(), applicationId, body.decision(), requestId);
        return reviews.decide(actor.id(), idempotencyKey,
                new DecisionRequest(applicationId, body.decision(), body.reason(), body.revision(), body.expectedVersion()), requestId);
    }

    @PostMapping("/api/v1/ops/kitchens/{kitchenId}/operating-state")
    @PreAuthorize("hasAuthority('PERM_cook.review') and @staffAssurance.recentMfa(authentication)")
    KitchenAdmin operatingState(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID kitchenId,
                                @Valid @RequestBody OperatingStateBody body, HttpServletRequest request) {
        return kitchens.changeOperatingState(actor.id(), kitchenId, body.action(), body.reason(), body.expectedVersion(),
                Requests.id(request));
    }
}
