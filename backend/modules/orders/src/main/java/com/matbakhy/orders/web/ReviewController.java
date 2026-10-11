package com.matbakhy.orders.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.orders.internal.ReviewService;
import com.matbakhy.orders.internal.ReviewService.Review;
import com.matbakhy.orders.internal.ReviewService.ReviewPage;
import com.matbakhy.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-007, API-029, API-030 and API-073: verified-order reviews, reports and staff moderation. */
@RestController
class ReviewController {

    private final ReviewService reviews;

    ReviewController(ReviewService reviews) {
        this.reviews = reviews;
    }

    record ReviewBody(@NotNull Integer rating, @Size(max = 1000) String text) {
    }

    record ReportBody(@NotNull @Size(max = 40) String reasonCode, @Size(max = 500) String description) {
    }

    record ModerationBody(@NotNull String visibility, @Size(max = 1000) String reason, @NotNull Long expectedVersion) {
    }

    @GetMapping("/api/v1/kitchens/{kitchenId}/reviews")
    ReviewPage kitchenReviews(@PathVariable UUID kitchenId, @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return reviews.kitchenReviews(kitchenId, cursor, limit);
    }

    @PostMapping("/api/v1/orders/{orderId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    Review create(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID orderId, @Valid @RequestBody ReviewBody body) {
        return reviews.create(actor.id(), orderId, body.rating(), body.text());
    }

    @PostMapping("/api/v1/reviews/{reviewId}/reports")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void report(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID reviewId, @Valid @RequestBody ReportBody body) {
        reviews.report(actor.id(), reviewId, body.reasonCode(), body.description());
    }

    @PostMapping("/api/v1/ops/reviews/{reviewId}/moderation")
    @PreAuthorize("hasAuthority('PERM_content.moderate')")
    Review moderate(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID reviewId, @Valid @RequestBody ModerationBody body,
                    HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return reviews.moderate(actor.id(), reviewId, body.visibility(), body.reason(), body.expectedVersion(), requestId == null ? null : requestId.toString());
    }
}
