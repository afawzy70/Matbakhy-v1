package com.matbakhy.app.mfa;

import com.matbakhy.app.security.StaffAssurance;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Administrator-controlled MFA policy per account category plus each user's own preference (owner decision D18).
 * The authentication factor itself lives in Keycloak; this class only decides whether MFA is needed for an account
 * and is the single server-side source for that decision. A user's preference can never relax a REQUIRED policy.
 */
@Service
public class MfaPolicyService {

    public enum Category { CUSTOMER, KITCHEN, STAFF }

    /** Ordered from least to most protective: the effective mode of an account is the most protective applicable one. */
    public enum Mode { DISABLED, OPTIONAL, REQUIRED }

    public record Policy(Category category, Mode mode, String reason, UUID updatedBy, Instant updatedAt, long version) {
    }

    /**
     * @param categories the categories this account belongs to (every account is a CUSTOMER)
     * @param effectiveMode most protective mode among them
     * @param preference the user's own setting (kept even while the policy makes it irrelevant)
     * @param required MFA must be asserted on the session for protected functions
     * @param available enrolment and use are offered (false only when every applicable category is DISABLED)
     */
    public record Decision(Set<Category> categories, Map<Category, Mode> modes, Mode effectiveMode, boolean preference,
                           boolean required, boolean available, Instant resetAt) {

        /**
         * The session/token passed MFA <em>and</em> did so after the account's last administrative reset; an assertion
         * older than the reset no longer counts (a reset invalidates earlier proofs, not only the provider sessions).
         */
        public boolean satisfiedBy(Map<String, Object> claims, List<String> mfaAcrValues) {
            if (!StaffAssurance.assertsMfa(claims, mfaAcrValues)) {
                return false;
            }
            if (resetAt == null) {
                return true;
            }
            Instant authTime = StaffAssurance.authTime(claims);
            return authTime != null && !authTime.isBefore(resetAt);
        }
    }

    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final Clock clock;

    MfaPolicyService(JdbcTemplate jdbc, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Policy> policies() {
        return jdbc.query("SELECT category, mode, reason, updated_by, updated_at, version FROM mfa_policy ORDER BY category",
                (rs, n) -> new Policy(Category.valueOf(rs.getString(1)), Mode.valueOf(rs.getString(2)), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getTimestamp(5).toInstant(), rs.getLong(6)));
    }

    /** One read per request: the account's categories, its preference and the three policies. */
    @Transactional(readOnly = true)
    public Decision decide(UUID accountId) {
        Map<String, Object> facts = jdbc.queryForMap("""
                SELECT EXISTS (SELECT 1 FROM kitchen WHERE owner_id = ?) AS kitchen,
                       EXISTS (SELECT 1 FROM user_grant WHERE user_id = ? AND revoked_at IS NULL
                                                           AND (expires_at IS NULL OR expires_at > now())) AS staff,
                       coalesce((SELECT enabled FROM user_mfa_preference WHERE user_id = ?), false) AS preference,
                       (SELECT reset_at FROM user_mfa_reset WHERE user_id = ?) AS reset_at""",
                accountId, accountId, accountId, accountId);
        Set<Category> categories = EnumSet.of(Category.CUSTOMER);
        if (Boolean.TRUE.equals(facts.get("kitchen"))) {
            categories.add(Category.KITCHEN);
        }
        if (Boolean.TRUE.equals(facts.get("staff"))) {
            categories.add(Category.STAFF);
        }
        Map<Category, Mode> all = new EnumMap<>(Category.class);
        policies().forEach(p -> all.put(p.category(), p.mode()));
        Timestamp resetAt = (Timestamp) facts.get("reset_at");
        Decision base = resolve(categories, all, Boolean.TRUE.equals(facts.get("preference")));
        return new Decision(base.categories(), base.modes(), base.effectiveMode(), base.preference(), base.required(), base.available(),
                resetAt == null ? null : resetAt.toInstant());
    }

    /** The decision for the account bound to a provider subject, if one exists (used before a session is mapped). */
    @Transactional(readOnly = true)
    public java.util.Optional<Decision> decideForSubject(String issuer, String subject) {
        return jdbc.query("SELECT id FROM app_user WHERE issuer = ? AND subject = ?", (rs, n) -> rs.getObject(1, UUID.class), issuer, subject)
                .stream().findFirst().map(this::decide);
    }

    /** Records an administrative reset: earlier MFA assertions of this account stop counting. */
    @Transactional
    public void recordReset(UUID accountId, UUID byAdmin) {
        jdbc.update("""
                INSERT INTO user_mfa_reset (user_id, reset_at, reset_by) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET reset_at = EXCLUDED.reset_at, reset_by = EXCLUDED.reset_by""",
                accountId, Timestamp.from(clock.instant()), byAdmin);
    }

    /** Pure precedence rules; unit-tested without a database. */
    public static Decision resolve(Set<Category> categories, Map<Category, Mode> policy, boolean preference) {
        Map<Category, Mode> applicable = new EnumMap<>(Category.class);
        Mode effective = Mode.DISABLED;
        for (Category c : categories) {
            // A missing policy row fails safe to REQUIRED rather than silently disabling MFA.
            Mode mode = policy.getOrDefault(c, Mode.REQUIRED);
            applicable.put(c, mode);
            if (mode.compareTo(effective) > 0) {
                effective = mode;
            }
        }
        boolean required = effective == Mode.REQUIRED || (effective == Mode.OPTIONAL && preference);
        return new Decision(Set.copyOf(categories), Map.copyOf(applicable), effective, preference, required, effective != Mode.DISABLED, null);
    }

    @Transactional
    public void setPreference(UUID accountId, boolean enabled) {
        jdbc.update("""
                INSERT INTO user_mfa_preference (user_id, enabled, updated_at) VALUES (?, ?, ?)
                ON CONFLICT (user_id) DO UPDATE SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at""",
                accountId, enabled, Timestamp.from(clock.instant()));
    }

    @Transactional
    public Policy update(UUID actorId, Category category, Mode mode, long expectedVersion, String reason, String requestId) {
        Policy current = jdbc.query("SELECT category, mode, reason, updated_by, updated_at, version FROM mfa_policy WHERE category = ? FOR UPDATE",
                (rs, n) -> new Policy(Category.valueOf(rs.getString(1)), Mode.valueOf(rs.getString(2)), rs.getString(3),
                        rs.getObject(4, UUID.class), rs.getTimestamp(5).toInstant(), rs.getLong(6)), category.name())
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "policy not found"));
        if (current.version() != expectedVersion) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale MFA policy version");
        }
        if (reason == null || reason.strip().length() < 3) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "reason required",
                    List.of(new FieldError("reason", "REQUIRED")));
        }
        jdbc.update("UPDATE mfa_policy SET mode = ?, reason = ?, updated_by = ?, updated_at = ?, version = version + 1 WHERE category = ?",
                mode.name(), reason.strip(), actorId, Timestamp.from(clock.instant()), category.name());
        Map<String, Object> details = new java.util.LinkedHashMap<>();
        details.put("category", category.name());
        details.put("from", current.mode().name());
        details.put("to", mode.name());
        audit.record(new Entry(actorId, "mfa.policy.update", "security.mfa_policy", "mfa_policy", category.name(), Outcome.ALLOWED,
                reason.strip(), requestId, details));
        return new Policy(category, mode, reason.strip(), actorId, clock.instant(), current.version() + 1);
    }
}
