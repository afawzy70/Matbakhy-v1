package com.matbakhy.cooks.internal;

import com.matbakhy.cooks.api.KitchenDirectory;
import com.matbakhy.cooks.internal.CookApplications.Revision;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.security.SecureRandom;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kitchens and their operating state (BR-009), kept separate from the application decision. Only operations
 * staff suspend or restore a suspension. Public views never include the private location or contact phone.
 */
@Service
public class KitchenService implements KitchenDirectory {

    public enum Action { PAUSE, SUSPEND, RESTORE }

    public record KitchenAdmin(UUID kitchenId, UUID ownerId, String publicSlug, String displayName, String operatingState,
                               String stateReason, int approvedRevision, long version) {
    }

    /** API-005 public profile. Approval is an operational verification label, not a food-safety certification. */
    public record KitchenPublic(UUID kitchenId, String slug, String displayName, String description, String verificationLabel,
                                boolean available, boolean pickupEnabled, List<UUID> deliveryZoneIds) {
    }

    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final Clock clock;

    KitchenService(JdbcTemplate jdbc, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Override
    public Optional<KitchenStatus> find(UUID kitchenId) {
        return jdbc.query("SELECT id, owner_id, operating_state FROM kitchen WHERE id = ?", KitchenService::status, kitchenId)
                .stream().findFirst();
    }

    @Override
    public Optional<KitchenStatus> ownedBy(UUID ownerId) {
        return jdbc.query("SELECT id, owner_id, operating_state FROM kitchen WHERE owner_id = ?", KitchenService::status, ownerId)
                .stream().findFirst();
    }

    /** Called inside the approval transaction. */
    void createFromApproval(UUID applicationId, UUID ownerId, Revision approved) {
        UUID kitchenId = UUID.randomUUID();
        byte[] suffix = new byte[5];
        RANDOM.nextBytes(suffix);
        String slug = "kitchen-" + HexFormat.of().formatHex(suffix);
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("""
                INSERT INTO kitchen (id, owner_id, application_id, approved_revision, public_slug, display_name, description,
                                     pickup_enabled, contact_phone, private_location, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                kitchenId, ownerId, applicationId, approved.revision(), slug, approved.publicName(), approved.bio(),
                approved.pickupEnabled(), approved.contactPhone(), approved.privateLocation(), now, now);
        for (UUID zone : approved.zoneIds()) {
            jdbc.update("INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES (?, ?)", kitchenId, zone);
        }
    }

    public KitchenPublic publicProfile(String slug) {
        return jdbc.query("""
                SELECT id, public_slug, display_name, description, operating_state, pickup_enabled FROM kitchen
                WHERE public_slug = ? AND operating_state <> 'CLOSED'""",
                (rs, n) -> new KitchenPublic(rs.getObject("id", UUID.class), rs.getString("public_slug"), rs.getString("display_name"),
                        rs.getString("description"), "OPERATIONALLY_VERIFIED", "ACTIVE".equals(rs.getString("operating_state")),
                        rs.getBoolean("pickup_enabled"), deliveryZones(rs.getObject("id", UUID.class))), slug)
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "kitchen not found"));
    }

    public KitchenAdmin admin(UUID kitchenId) {
        return jdbc.query("""
                SELECT id, owner_id, public_slug, display_name, operating_state, state_reason, approved_revision, version
                FROM kitchen WHERE id = ?""", KitchenService::admin, kitchenId)
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "kitchen not found"));
    }

    @Transactional(noRollbackFor = ApiException.class)
    public KitchenAdmin changeOperatingState(UUID actorId, UUID kitchenId, Action action, String reason, Long expectedVersion,
                                             String requestId) {
        KitchenAdmin kitchen = jdbc.query("""
                SELECT id, owner_id, public_slug, display_name, operating_state, state_reason, approved_revision, version
                FROM kitchen WHERE id = ? FOR UPDATE""", KitchenService::admin, kitchenId)
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "kitchen not found"));
        if (actorId.equals(kitchen.ownerId())) {
            audit.record(new Entry(actorId, "kitchen.operating_state", Permission.COOK_REVIEW.code(), "kitchen", kitchenId.toString(),
                    Outcome.DENIED, "staff cannot change the state of their own kitchen", requestId, Map.of("action", action.name())));
            throw new ApiException(ErrorCode.FORBIDDEN, "own kitchen");
        }
        String trimmed = reason == null ? null : reason.strip();
        if (trimmed == null || trimmed.length() < 3 || trimmed.length() > 1000) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "reason required", List.of(new FieldError("reason", "LENGTH_3_1000")));
        }
        if (expectedVersion == null || expectedVersion != kitchen.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale kitchen version");
        }
        String from = kitchen.operatingState();
        String to = switch (action) {
            case PAUSE -> "ACTIVE".equals(from) ? "PAUSED" : null;
            case SUSPEND -> "ACTIVE".equals(from) || "PAUSED".equals(from) ? "SUSPENDED" : null;
            case RESTORE -> "PAUSED".equals(from) || "SUSPENDED".equals(from) ? "ACTIVE" : null;
        };
        if (to == null) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, action + " not allowed from " + from);
        }
        jdbc.update("UPDATE kitchen SET operating_state = ?, state_reason = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                to, trimmed, Timestamp.from(clock.instant()), kitchenId);
        audit.record(new Entry(actorId, "kitchen.operating_state", Permission.COOK_REVIEW.code(), "kitchen", kitchenId.toString(),
                Outcome.ALLOWED, trimmed, requestId, Map.of("action", action.name(), "from", from, "to", to)));
        return admin(kitchenId);
    }

    private List<UUID> deliveryZones(UUID kitchenId) {
        return jdbc.queryForList("SELECT zone_id FROM kitchen_zone WHERE kitchen_id = ? AND delivery_enabled ORDER BY zone_id",
                UUID.class, kitchenId);
    }

    private static KitchenStatus status(ResultSet rs, int n) throws SQLException {
        return new KitchenStatus(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                OperatingState.valueOf(rs.getString("operating_state")));
    }

    private static KitchenAdmin admin(ResultSet rs, int n) throws SQLException {
        return new KitchenAdmin(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("public_slug"),
                rs.getString("display_name"), rs.getString("operating_state"), rs.getString("state_reason"),
                rs.getInt("approved_revision"), rs.getLong("version"));
    }
}
