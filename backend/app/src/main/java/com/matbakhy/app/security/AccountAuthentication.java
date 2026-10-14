package com.matbakhy.app.security;

import com.matbakhy.identity.api.CurrentAccount;
import java.util.Collection;
import java.util.Map;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

/**
 * Authenticated request bound to a local account. Authorities are assigned only from server-side
 * state (account status now, grants in MB-011); provider token claims never grant authority.
 */
public final class AccountAuthentication extends AbstractAuthenticationToken {

    private final CurrentAccount account;
    private final Map<String, Object> identityClaims;

    AccountAuthentication(CurrentAccount account, Map<String, Object> identityClaims,
                          Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.account = account;
        this.identityClaims = Map.copyOf(identityClaims);
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public CurrentAccount getPrincipal() {
        return account;
    }

    @Override
    public String getName() {
        return account.id().toString();
    }

    /** Validated provider claims (acr, amr, auth_time) used for assurance checks, never for authorization grants. */
    public Map<String, Object> identityClaims() {
        return identityClaims;
    }
}
