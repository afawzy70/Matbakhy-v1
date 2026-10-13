package com.matbakhy.finance.web;

import com.matbakhy.finance.internal.InsightsService;
import com.matbakhy.finance.internal.InsightsService.CookInsights;
import com.matbakhy.identity.api.CurrentAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-111 (MB-054): the cook's own free activity and sales report; the kitchen comes from the caller, never a parameter. */
@RestController
class InsightsController {

    private final InsightsService insights;

    InsightsController(InsightsService insights) {
        this.insights = insights;
    }

    @GetMapping("/api/v1/cook/insights")
    CookInsights insights(@AuthenticationPrincipal CurrentAccount actor, @RequestParam(required = false) String from,
                          @RequestParam(required = false) String to) {
        return insights.forCook(actor.id(), from, to);
    }
}
