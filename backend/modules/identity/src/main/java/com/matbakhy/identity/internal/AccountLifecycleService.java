package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.CheckoutContactPolicy;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Checkout contact policy (BR-003) and account deletion intake. */
@Service
public class AccountLifecycleService implements CheckoutContactPolicy {

    public record DeletionRequest(UUID id, String state, String policyVersion, java.time.Instant requestedAt) {
    }

    private final JdbcTemplate jdbc;

    AccountLifecycleService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void requireVerifiedContact(UUID accountId) {
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT email_verified, phone_e164, phone_verified_at FROM app_user WHERE id = ?", accountId);
        if (!Boolean.TRUE.equals(row.get("email_verified")) || row.get("phone_e164") == null || row.get("phone_verified_at") == null) {
            throw new ApiException(ErrorCode.CONTACT_VERIFICATION_REQUIRED, "contact verification required");
        }
    }

    @Override
    public VerifiedContact verifiedContact(UUID accountId) {
        requireVerifiedContact(accountId);
        return jdbc.queryForObject("SELECT display_name, phone_e164 FROM app_user WHERE id = ?",
                (rs, n) -> new VerifiedContact(rs.getString(1), rs.getString(2)), accountId);
    }

    /**
     * Records the request and disables the account at once. Data purge/anonymization follows the approved
     * retention policy (O06, open) and is not performed here. Repeating the request returns the open one.
     */
    @Transactional
    DeletionRequest requestDeletion(UUID accountId, String policyVersion) {
        jdbc.update("""
                INSERT INTO deletion_request (id, user_id, policy_version) VALUES (?, ?, ?)
                ON CONFLICT (user_id) WHERE state = 'RECEIVED' DO NOTHING""", UUID.randomUUID(), accountId, policyVersion);
        jdbc.update("UPDATE app_user SET status = 'DELETION_REQUESTED', version = version + 1, updated_at = now() WHERE id = ? AND status = 'ACTIVE'",
                accountId);
        return jdbc.queryForObject("""
                SELECT id, state, policy_version, requested_at FROM deletion_request WHERE user_id = ? AND state = 'RECEIVED'""",
                (rs, i) -> new DeletionRequest(rs.getObject("id", UUID.class), rs.getString("state"), rs.getString("policy_version"),
                        rs.getTimestamp("requested_at").toInstant()), accountId);
    }
}
