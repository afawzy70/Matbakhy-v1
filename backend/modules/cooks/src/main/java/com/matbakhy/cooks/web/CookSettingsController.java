package com.matbakhy.cooks.web;

import com.matbakhy.cooks.api.KitchenDirectory.KitchenSettings;
import com.matbakhy.cooks.internal.KitchenService;
import com.matbakhy.cooks.internal.KitchenService.SettingsPatch;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import com.matbakhy.shared.web.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** API-065: the cook's own kitchen settings. The current settings are part of the dashboard (API-045). */
@RestController
class CookSettingsController {

    private final KitchenService kitchens;

    CookSettingsController(KitchenService kitchens) {
        this.kitchens = kitchens;
    }

    record ZoneToggle(@NotNull UUID zoneId, @NotNull Boolean deliveryEnabled) {
    }

    record SettingsBody(@Size(max = 2000) String description, Boolean pickupEnabled, @Size(max = 50) List<@Valid ZoneToggle> deliveryZones,
                        Boolean paused, @NotNull Long expectedVersion) {
    }

    @PatchMapping("/api/v1/cook/settings")
    KitchenSettings update(@AuthenticationPrincipal CurrentAccount actor, @Valid @RequestBody SettingsBody body, HttpServletRequest request) {
        Map<UUID, Boolean> zones = null;
        if (body.deliveryZones() != null) {
            zones = new LinkedHashMap<>();
            for (ZoneToggle z : body.deliveryZones()) {
                if (zones.put(z.zoneId(), z.deliveryEnabled()) != null) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED, "duplicate zone", List.of(new FieldError("deliveryZones", "DUPLICATE")));
                }
            }
        }
        Object requestId = request.getAttribute(RequestContext.REQUEST_ID_ATTRIBUTE);
        return kitchens.updateSettings(actor.id(), new SettingsPatch(body.description(), body.pickupEnabled(), zones, body.paused(),
                body.expectedVersion()), requestId == null ? null : requestId.toString());
    }
}
