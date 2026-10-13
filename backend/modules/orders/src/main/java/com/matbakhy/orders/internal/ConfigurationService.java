package com.matbakhy.orders.internal;

import com.matbakhy.catalog.api.ZoneAdministration;
import com.matbakhy.catalog.api.ZoneAdministration.Zone;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.orders.api.SyntheticPolicyPermission;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Operating configuration (BR-042): commerce policy versions, zone delivery fees and availability, and feature flags.
 * Every change creates a new version with a reason and is recorded append-only and audited; only new quotes use it
 * (existing quotes fail revalidation with POLICY_CHANGED / TOTAL_CHANGED, placed orders keep their snapshots). A real
 * (non-synthetic) commerce policy goes live only with an approval reference; synthetic policies only in LOCAL/CI.
 * Scheduled (future-dated) changes are not supported yet.
 */
@Service
public class ConfigurationService {

    static final Set<String> FLAGS = Set.of("ordering-enabled");

    public record PolicyView(int version, String label, boolean synthetic, String status, int taxRateBps, int commissionRateBps, int maxItemQuantity,
                             int quoteTtlSeconds, int acceptanceWindowMinutes, int minAcceptanceMinutes, String cancellationPolicyVersion,
                             String termsVersion, Instant approvedAt) {
    }

    public record Flag(String key, boolean enabled, long version) {
    }

    public record Change(UUID id, String key, long version, JsonNode value, String reason, String approvalReference, UUID actorId, Instant createdAt) {
    }

    public record Configuration(PolicyView commercePolicy, List<PolicyView> policyHistory, List<Zone> zones, List<Flag> flags, List<Change> changes) {
    }

    private final JdbcTemplate jdbc;
    private final ZoneAdministration zones;
    private final SyntheticPolicyPermission synthetic;
    private final AuditLog audit;
    private final JsonMapper json;
    private final Clock clock;

    ConfigurationService(JdbcTemplate jdbc, ZoneAdministration zones, SyntheticPolicyPermission synthetic, AuditLog audit, JsonMapper json, Clock clock) {
        this.jdbc = jdbc;
        this.zones = zones;
        this.synthetic = synthetic;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    public Configuration view() {
        List<PolicyView> history = jdbc.query("SELECT * FROM commerce_policy ORDER BY version DESC LIMIT 10", (rs, n) -> new PolicyView(rs.getInt("version"),
                rs.getString("label"), rs.getBoolean("synthetic"), rs.getString("status"), rs.getInt("tax_rate_bps"), rs.getInt("commission_rate_bps"),
                rs.getInt("max_item_quantity"), rs.getInt("quote_ttl_seconds"), rs.getInt("acceptance_window_minutes"), rs.getInt("min_acceptance_minutes"),
                rs.getString("cancellation_policy_version"), rs.getString("terms_version"),
                rs.getTimestamp("approved_at") == null ? null : rs.getTimestamp("approved_at").toInstant()));
        PolicyView active = history.stream().filter(p -> "ACTIVE".equals(p.status())).findFirst().orElse(null);
        List<Flag> flags = jdbc.query("SELECT key, enabled, version FROM feature_flag ORDER BY key", (rs, n) -> new Flag(rs.getString(1), rs.getBoolean(2),
                rs.getLong(3)));
        List<Change> changes = jdbc.query("SELECT id, config_key, resulting_version, value::text, reason, approval_reference, actor_id, created_at "
                + "FROM config_change ORDER BY created_at DESC, id LIMIT 50", (rs, n) -> new Change(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getLong(3), json.readTree(rs.getString(4)), rs.getString(5), rs.getString(6), rs.getObject(7, UUID.class), rs.getTimestamp(8).toInstant()));
        return new Configuration(active, history, zones.zones(), flags, changes);
    }

    @Transactional
    public Change newVersion(UUID staffId, String key, JsonNode value, Instant effectiveAt, String reason, String approvalReference, Long expectedVersion,
                             String requestId) {
        Instant now = clock.instant();
        if (effectiveAt != null && effectiveAt.isAfter(now.plusSeconds(60))) {
            throw invalid("effectiveAt", "SCHEDULED_CHANGES_NOT_SUPPORTED");
        }
        String why = text("reason", reason, 3, 1000);
        String approval = approvalReference == null || approvalReference.isBlank() ? null : text("approvalReference", approvalReference, 3, 200);
        if (value == null || !value.isObject()) {
            throw invalid("value", "OBJECT_REQUIRED");
        }
        if (expectedVersion == null) {
            throw invalid("expectedVersion", "REQUIRED");
        }
        long resulting;
        Map<String, Object> recorded = new LinkedHashMap<>();
        if ("commerce-policy".equals(key)) {
            resulting = newPolicy(staffId, value, approval, expectedVersion, recorded, now);
        } else if (key != null && key.startsWith("zone:")) {
            UUID zoneId;
            try {
                zoneId = UUID.fromString(key.substring(5));
            } catch (IllegalArgumentException e) {
                throw new ApiException(ErrorCode.NOT_FOUND, "unknown configuration key");
            }
            zones.zone(zoneId).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "unknown zone"));
            boolean active = bool(value, "active");
            long fee = integer(value, "deliveryFeeMinor", 0, 100_000);
            Zone z = zones.update(zoneId, expectedVersion, active, fee).orElseThrow(() -> new ApiException(ErrorCode.VERSION_CONFLICT, "stale zone version"));
            recorded.put("active", active);
            recorded.put("deliveryFeeMinor", fee);
            resulting = z.version();
        } else if (key != null && key.startsWith("flag:") && FLAGS.contains(key.substring(5))) {
            boolean enabled = bool(value, "enabled");
            int n = jdbc.update("UPDATE feature_flag SET enabled = ?, updated_at = ?, version = version + 1 WHERE key = ? AND version = ?", enabled,
                    Timestamp.from(now), key.substring(5), expectedVersion);
            if (n == 0) {
                throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale flag version");
            }
            recorded.put("enabled", enabled);
            resulting = expectedVersion + 1;
        } else {
            throw new ApiException(ErrorCode.NOT_FOUND, "unknown configuration key");
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO config_change (id, config_key, resulting_version, value, reason, approval_reference, actor_id, created_at)
                VALUES (?, ?, ?, ?::jsonb, ?, ?, ?, ?)""", id, key, resulting, json.writeValueAsString(recorded), why, approval, staffId, Timestamp.from(now));
        audit.record(new Entry(staffId, "config.change", Permission.CONFIG_WRITE.code(), "config", key, Outcome.ALLOWED, why, requestId,
                Map.of("version", resulting, "approvalReference", String.valueOf(approval))));
        return new Change(id, key, resulting, json.valueToTree(recorded), why, approval, staffId, now);
    }

    private long newPolicy(UUID staffId, JsonNode v, String approval, long expectedVersion, Map<String, Object> recorded, Instant now) {
        Integer active = jdbc.query("SELECT version FROM commerce_policy WHERE status = 'ACTIVE' FOR UPDATE", rs -> rs.next() ? rs.getInt(1) : null);
        if (active == null ? expectedVersion != 0 : expectedVersion != active) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale policy version");
        }
        boolean isSynthetic = bool(v, "synthetic");
        if (isSynthetic && !synthetic.allowed()) {
            throw invalid("synthetic", "NOT_ALLOWED_IN_THIS_ENVIRONMENT");
        }
        if (!isSynthetic && approval == null) {
            // Live approval guard: a real policy cannot go live without the owner's approval reference (O04).
            throw invalid("approvalReference", "REQUIRED_FOR_REAL_POLICY");
        }
        recorded.put("label", text("label", str(v, "label"), 3, 120));
        recorded.put("synthetic", isSynthetic);
        recorded.put("taxRateBps", integer(v, "taxRateBps", 0, 10_000));
        // Owner decision D10 (2026-10-13): no Matbakhy commission at launch. The rate stays in the policy as inactive
        // capability; only 0 is accepted (a database check also refuses a non-zero rate for a live policy).
        recorded.put("commissionRateBps", integer(v, "commissionRateBps", 0, 0));
        recorded.put("maxItemQuantity", integer(v, "maxItemQuantity", 1, 99));
        recorded.put("quoteTtlSeconds", integer(v, "quoteTtlSeconds", 60, 3600));
        recorded.put("acceptanceWindowMinutes", integer(v, "acceptanceWindowMinutes", 1, 1440));
        recorded.put("minAcceptanceMinutes", integer(v, "minAcceptanceMinutes", 1, 120));
        recorded.put("cancellationPolicyVersion", text("cancellationPolicyVersion", str(v, "cancellationPolicyVersion"), 1, 40));
        recorded.put("termsVersion", text("termsVersion", str(v, "termsVersion"), 1, 40));
        int version = jdbc.queryForObject("SELECT coalesce(max(version), 0) + 1 FROM commerce_policy", Integer.class);
        jdbc.update("UPDATE commerce_policy SET status = 'RETIRED' WHERE status = 'ACTIVE'");
        jdbc.update("""
                INSERT INTO commerce_policy (version, label, synthetic, status, tax_rate_bps, commission_rate_bps, max_item_quantity, quote_ttl_seconds,
                                             acceptance_window_minutes, min_acceptance_minutes, cancellation_policy_version, terms_version, approved_by,
                                             approved_at)
                VALUES (?, ?, ?, 'ACTIVE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                version, recorded.get("label"), isSynthetic, recorded.get("taxRateBps"), recorded.get("commissionRateBps"), recorded.get("maxItemQuantity"),
                recorded.get("quoteTtlSeconds"), recorded.get("acceptanceWindowMinutes"), recorded.get("minAcceptanceMinutes"),
                recorded.get("cancellationPolicyVersion"), recorded.get("termsVersion"), isSynthetic ? null : staffId,
                isSynthetic ? null : Timestamp.from(now));
        return version;
    }

    private static String str(JsonNode v, String field) {
        JsonNode n = v.get(field);
        return n == null || !n.isString() ? null : n.asString();
    }

    private static boolean bool(JsonNode v, String field) {
        JsonNode n = v.get(field);
        if (n == null || !n.isBoolean()) {
            throw invalid("value." + field, "BOOLEAN_REQUIRED");
        }
        return n.asBoolean();
    }

    private static int integer(JsonNode v, String field, int min, int max) {
        JsonNode n = v.get(field);
        if (n == null || !n.isIntegralNumber() || n.asLong() < min || n.asLong() > max) {
            throw invalid("value." + field, "RANGE_" + min + "_" + max);
        }
        return (int) n.asLong();
    }

    private static String text(String field, String value, int min, int max) {
        String v = value == null ? "" : value.strip();
        if (v.length() < min || v.length() > max) {
            throw invalid(field, "LENGTH_" + min + "_" + max);
        }
        return v;
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
