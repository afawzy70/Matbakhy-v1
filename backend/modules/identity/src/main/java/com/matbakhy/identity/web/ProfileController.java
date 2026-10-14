package com.matbakhy.identity.web;

import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.internal.ProfileFacade;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API-009 / API-010: the caller's own profile. Never accepts role, status or identity fields. */
@RestController
@RequestMapping("/api/v1/me")
class ProfileController {

    private final ProfileFacade profiles;

    ProfileController(ProfileFacade profiles) {
        this.profiles = profiles;
    }

    record CustomerProfile(UUID id, String displayName, String locale, boolean emailVerified, boolean phoneVerified,
                           long version, List<String> capabilities) {

        static CustomerProfile of(CurrentAccount a) {
            return new CustomerProfile(a.id(), a.displayName(), a.locale(), a.emailVerified(), a.phoneVerified(),
                    a.version(), List.of("BROWSE", "ORDER"));
        }
    }

    record UpdateProfileRequest(@NotNull @Min(0) Long expectedVersion,
                                @Size(min = 1, max = 120) String displayName,
                                @Pattern(regexp = "ar|en") String locale) {
    }

    @GetMapping
    CustomerProfile me(@AuthenticationPrincipal CurrentAccount account) {
        return CustomerProfile.of(account);
    }

    @PatchMapping
    CustomerProfile update(@AuthenticationPrincipal CurrentAccount account, @Valid @RequestBody UpdateProfileRequest request) {
        String name = request.displayName() == null ? null : request.displayName().strip();
        return CustomerProfile.of(profiles.update(account.id(), request.expectedVersion(), name, request.locale()));
    }
}
