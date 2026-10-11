package com.matbakhy.orders.internal;

import com.matbakhy.orders.api.SyntheticPolicyPermission;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * The single ACTIVE commerce policy. Ordering is blocked without one, when only a synthetic one exists outside LOCAL/CI,
 * or while the {@code ordering-enabled} flag is off (MB-026). Placed orders keep their snapshots.
 */
@Component
public class CommercePolicies {

    public record Policy(int version, boolean synthetic, int taxRateBps, int commissionRateBps, int maxItemQuantity, int quoteTtlSeconds,
                         int acceptanceWindowMinutes, int minAcceptanceMinutes, String cancellationPolicyVersion, String termsVersion) {
    }

    private final JdbcTemplate jdbc;
    private final SyntheticPolicyPermission synthetic;

    CommercePolicies(JdbcTemplate jdbc, SyntheticPolicyPermission synthetic) {
        this.jdbc = jdbc;
        this.synthetic = synthetic;
    }

    public Policy active() {
        Policy p = jdbc.query("""
                SELECT version, synthetic, tax_rate_bps, commission_rate_bps, max_item_quantity, quote_ttl_seconds, acceptance_window_minutes,
                       min_acceptance_minutes, cancellation_policy_version, terms_version
                FROM commerce_policy WHERE status = 'ACTIVE'""",
                (rs, n) -> new Policy(rs.getInt(1), rs.getBoolean(2), rs.getInt(3), rs.getInt(4), rs.getInt(5), rs.getInt(6), rs.getInt(7),
                        rs.getInt(8), rs.getString(9), rs.getString(10))).stream().findFirst().orElse(null);
        Boolean ordering = jdbc.query("SELECT enabled FROM feature_flag WHERE key = 'ordering-enabled'", rs -> rs.next() ? rs.getBoolean(1) : null);
        if (Boolean.FALSE.equals(ordering)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "ordering is paused by operations", List.of(new FieldError("ordering", "ORDERING_PAUSED")));
        }
        if (p == null || (p.synthetic() && !synthetic.allowed())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "no usable commerce policy",
                    List.of(new FieldError("policy", "NO_APPROVED_POLICY")));
        }
        return p;
    }
}
