package com.matbakhy.app.security;

import com.matbakhy.identity.api.AccountDirectory;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.api.ExternalIdentity;
import com.matbakhy.identity.api.GrantDirectory;
import com.matbakhy.identity.api.Permission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

/** Converts validated provider claims into an {@link AccountAuthentication}. */
@Component
class AccountMapper {

    static final String ACTIVE = "ACCOUNT_ACTIVE";
    /** Only lets a deletion-requested account repeat its deletion request (idempotent retry). */
    static final String DELETION_REQUESTED = "ACCOUNT_DELETION_REQUESTED";
    private static final List<String> ASSURANCE_CLAIMS = List.of("acr", "amr", "auth_time");

    private final AccountDirectory accounts;
    private final GrantDirectory grants;
    private final IdentityProperties properties;

    AccountMapper(AccountDirectory accounts, GrantDirectory grants, IdentityProperties properties) {
        this.accounts = accounts;
        this.grants = grants;
        this.properties = properties;
    }

    AccountAuthentication map(String issuer, String subject, Map<String, Object> claims) {
        Object verified = claims.get("email_verified");
        Object name = claims.getOrDefault("name", claims.get("preferred_username"));
        Object locale = claims.get("locale");
        CurrentAccount account = accounts.resolve(new ExternalIdentity(issuer, subject, Boolean.TRUE.equals(verified),
                name == null ? null : name.toString(), locale == null ? null : locale.toString()));
        if (account.active() && subject.equals(properties.bootstrapAdminSubject()) && issuer.equals(properties.issuer())) {
            grants.bootstrapAdministratorIfNone(account.id());
        }
        List<GrantedAuthority> authorities = new ArrayList<>();
        switch (account.status()) {
            case ACTIVE -> {
                authorities.add(new SimpleGrantedAuthority(ACTIVE));
                // Staff permissions come only from current server-side grants, re-read on every request.
                for (Permission p : grants.activePermissions(account.id())) {
                    authorities.add(new SimpleGrantedAuthority(p.authority()));
                }
            }
            case DELETION_REQUESTED -> authorities.add(new SimpleGrantedAuthority(DELETION_REQUESTED));
            default -> { }
        }
        Map<String, Object> assurance = new HashMap<>();
        for (String claim : ASSURANCE_CLAIMS) {
            if (claims.get(claim) != null) {
                assurance.put(claim, claims.get(claim));
            }
        }
        return new AccountAuthentication(account, assurance, authorities);
    }
}
