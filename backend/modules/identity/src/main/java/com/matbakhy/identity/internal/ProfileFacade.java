package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.CurrentAccount;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Module-internal entry point for the identity web layer. */
@Component
public class ProfileFacade {

    private final ProfileService service;

    ProfileFacade(ProfileService service) {
        this.service = service;
    }

    public CurrentAccount update(UUID accountId, long expectedVersion, String displayName, String locale) {
        return service.update(accountId, expectedVersion, displayName, locale);
    }
}
