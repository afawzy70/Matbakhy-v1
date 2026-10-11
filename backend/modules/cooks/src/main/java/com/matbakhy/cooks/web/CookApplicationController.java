package com.matbakhy.cooks.web;

import com.matbakhy.cooks.internal.CookApplicationService;
import com.matbakhy.cooks.internal.CookApplicationService.CookApplication;
import com.matbakhy.cooks.internal.CookApplicationService.DraftInput;
import com.matbakhy.identity.api.CurrentAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API-042..044: the caller's own cook application. Any active account may apply; nothing here grants cook status. */
@RestController
class CookApplicationController {

    private final CookApplicationService applications;

    CookApplicationController(CookApplicationService applications) {
        this.applications = applications;
    }

    record DraftRequest(@Size(max = 200) String publicName, @Size(max = 2000) String bio, @Size(max = 32) String contactPhone,
                        @Size(max = 600) String privateLocation, Boolean pickupEnabled, @Size(max = 50) List<@NotNull UUID> zoneIds,
                        @Min(0) Long expectedVersion) {
    }

    record SubmitRequest(@NotNull @Min(0) Long expectedVersion) {
    }

    @GetMapping("/api/v1/cook/application")
    CookApplication get(@AuthenticationPrincipal CurrentAccount actor) {
        return applications.get(actor.id());
    }

    @PutMapping("/api/v1/cook/application")
    CookApplication save(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody DraftRequest body) {
        return applications.saveDraft(actor.id(), body.expectedVersion(), new DraftInput(body.publicName(), body.bio(),
                body.contactPhone(), body.privateLocation(), body.pickupEnabled(), body.zoneIds()));
    }

    @PostMapping("/api/v1/cook/application/submit")
    CookApplication submit(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody SubmitRequest body,
                           HttpServletRequest request) {
        return applications.submit(actor.id(), body.expectedVersion(), Requests.id(request));
    }
}
