package com.matbakhy.app.security;

import com.matbakhy.identity.api.AccountDirectory;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.api.ExternalIdentity;
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
    private static final List<String> ASSURANCE_CLAIMS = List.of("acr", "amr", "auth_time");

    private final AccountDirectory accounts;

    AccountMapper(AccountDirectory accounts) {
        this.accounts = accounts;
    }

    AccountAuthentication map(String issuer, String subject, Map<String, Object> claims) {
        Object verified = claims.get("email_verified");
        Object name = claims.getOrDefault("name", claims.get("preferred_username"));
        Object locale = claims.get("locale");
        CurrentAccount account = accounts.resolve(new ExternalIdentity(issuer, subject, Boolean.TRUE.equals(verified),
                name == null ? null : name.toString(), locale == null ? null : locale.toString()));
        List<GrantedAuthority> authorities = account.active() ? List.of(new SimpleGrantedAuthority(ACTIVE)) : List.of();
        Map<String, Object> assurance = new HashMap<>();
        for (String claim : ASSURANCE_CLAIMS) {
            if (claims.get(claim) != null) {
                assurance.put(claim, claims.get(claim));
            }
        }
        return new AccountAuthentication(account, assurance, authorities);
    }
}
