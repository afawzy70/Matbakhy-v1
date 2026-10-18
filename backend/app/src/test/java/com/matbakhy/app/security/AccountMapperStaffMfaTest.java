package com.matbakhy.app.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.identity.api.AccountDirectory;
import com.matbakhy.identity.api.AccountStatus;
import com.matbakhy.identity.api.CurrentAccount;
import com.matbakhy.identity.api.ExternalIdentity;
import com.matbakhy.identity.api.GrantDirectory;
import com.matbakhy.identity.api.Permission;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** BR-007 defense in depth: with require-staff-mfa, staff permissions need the provider's MFA assertion on the token. */
class AccountMapperStaffMfaTest {

    private static final UUID ID = UUID.randomUUID();
    private static final IdentityProperties PROPS = new IdentityProperties("https://idp.test.invalid/realms/x", null, "matbakhy-api",
            "web", "s", List.of("http://localhost:5173"), "http://localhost:5173/", List.of("mfa", "2"), Duration.ofMinutes(10), null, null);

    private static AccountMapper mapper(boolean require) {
        AccountDirectory accounts = new AccountDirectory() {
            @Override public CurrentAccount resolve(ExternalIdentity identity) { return new CurrentAccount(ID, AccountStatus.ACTIVE, "Staff", "en", true, false, 0); }
            @Override public Optional<CurrentAccount> find(UUID accountId) { return Optional.empty(); }
        };
        GrantDirectory grants = new GrantDirectory() {
            @Override public Set<Permission> activePermissions(UUID accountId) { return Set.of(Permission.AUDIT_READ); }
            @Override public void bootstrapAdministratorIfNone(UUID accountId) { }
        };
        return new AccountMapper(accounts, grants, PROPS, require);
    }

    private static boolean hasAudit(AccountAuthentication a) {
        return a.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("PERM_audit.read"));
    }

    @Test
    void default_behaviour_is_unchanged_so_the_flag_can_be_enabled_after_the_provider_enforces_mfa() {
        assertThat(hasAudit(mapper(false).map("https://idp.test.invalid/realms/x", "sub", Map.of("acr", "1")))).isTrue();
    }

    @Test
    void with_the_flag_a_password_only_token_gets_no_staff_permission_but_stays_an_active_account() {
        AccountAuthentication passwordOnly = mapper(true).map("https://idp.test.invalid/realms/x", "sub", Map.of("acr", "1", "amr", List.of("pwd")));
        assertThat(hasAudit(passwordOnly)).isFalse();
        assertThat(passwordOnly.getAuthorities().stream().anyMatch(g -> g.getAuthority().equals("ACCOUNT_ACTIVE"))).isTrue();
    }

    @Test
    void with_the_flag_an_mfa_token_keeps_its_permissions() {
        assertThat(hasAudit(mapper(true).map("https://idp.test.invalid/realms/x", "sub", Map.of("acr", "1", "amr", List.of("pwd", "otp"))))).isTrue();
    }
}
