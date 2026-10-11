package com.matbakhy.catalog.web;

import com.matbakhy.catalog.internal.meal.MealService;
import com.matbakhy.catalog.internal.meal.MealService.CookMeal;
import com.matbakhy.catalog.internal.meal.MealService.Moderation;
import com.matbakhy.catalog.internal.meal.MealService.OpsMealPage;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.shared.web.RequestContext;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-071/072: catalogue moderation by staff with content.moderate (reasoned, audited, separate from cook publication). */
@RestController
class ModerationController {

    private final MealService meals;

    ModerationController(MealService meals) {
        this.meals = meals;
    }

    record ModerationBody(@NotNull Moderation action, @Size(max = 1000) String reason, @NotNull @Min(0) Long expectedVersion) {
    }

    @GetMapping("/api/v1/ops/catalogue")
    @PreAuthorize("hasAuthority('PERM_content.moderate')")
    OpsMealPage catalogue(@RequestParam(required = false) String status, @RequestParam(required = false) String moderationState,
                          @RequestParam(required = false) UUID kitchenId, @RequestParam(required = false) String q,
                          @RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit) {
        return meals.catalogue(status, moderationState, kitchenId, q, cursor, limit);
    }

    @PostMapping("/api/v1/ops/meals/{mealId}/moderation")
    @PreAuthorize("hasAuthority('PERM_content.moderate')")
    CookMeal moderate(@AuthenticationPrincipal CurrentAccount actor, @PathVariable UUID mealId, @Valid @RequestBody ModerationBody body,
                      HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return meals.moderate(actor.id(), mealId, body.action(), body.reason(), body.expectedVersion(), requestId == null ? null : requestId.toString());
    }
}
