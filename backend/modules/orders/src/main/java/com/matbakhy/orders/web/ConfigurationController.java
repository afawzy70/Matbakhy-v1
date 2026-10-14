package com.matbakhy.orders.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.orders.internal.ConfigurationService;
import com.matbakhy.orders.internal.ConfigurationService.Change;
import com.matbakhy.orders.internal.ConfigurationService.Configuration;
import com.matbakhy.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;

/** API-089/090: operating configuration; changes need config.write and recent MFA. */
@RestController
class ConfigurationController {

    private final ConfigurationService configuration;

    ConfigurationController(ConfigurationService configuration) {
        this.configuration = configuration;
    }

    record VersionBody(@NotNull JsonNode value, Instant effectiveAt, @Size(max = 1000) String reason, @Size(max = 200) String approvalReference,
                       @NotNull Long expectedVersion) {
    }

    @GetMapping("/api/v1/ops/configuration")
    @PreAuthorize("hasAuthority('PERM_config.read') or hasAuthority('PERM_config.write')")
    Configuration view() {
        return configuration.view();
    }

    @PostMapping("/api/v1/ops/configuration/{key}/versions")
    @PreAuthorize("hasAuthority('PERM_config.write') and @staffAssurance.recentMfa(authentication)")
    Change newVersion(@AuthenticationPrincipal CurrentAccount actor, @PathVariable String key, @Valid @RequestBody VersionBody body,
                      HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return configuration.newVersion(actor.id(), key, body.value(), body.effectiveAt(), body.reason(), body.approvalReference(), body.expectedVersion(),
                requestId == null ? null : requestId.toString());
    }
}
