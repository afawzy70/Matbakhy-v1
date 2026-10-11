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
import java.time.Instant;
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

    @Override
    public Optional<FulfillmentContact> fulfillmentContact(UUID kitchenId) {
        return jdbc.query("SELECT display_name, contact_phone, private_location FROM kitchen WHERE id = ?",
                (rs, n) -> new FulfillmentContact(rs.getString(1), rs.getString(2), rs.getString(3)), kitchenId).stream().findFirst();
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
        jdbc.update("""
                UPDATE kitchen SET operating_state = ?, paused_by = ?, state_reason = ?, updated_at = ?, version = version + 1 WHERE id = ?""",
                to, "PAUSED".equals(to) ? "STAFF" : null, trimmed, Timestamp.from(clock.instant()), kitchenId);
        audit.record(new Entry(actorId, "kitchen.operating_state", Permission.COOK_REVIEW.code(), "kitchen", kitchenId.toString(),
                Outcome.ALLOWED, trimmed, requestId, Map.of("action", action.name(), "from", from, "to", to)));
        return admin(kitchenId);
    }

    // ---- cook workspace settings (API-065) ----

    /** Optional changes; null fields stay as they are. */
    public record SettingsPatch(String description, Boolean pickupEnabled, Map<UUID, Boolean> deliveryZones, Boolean paused,
                                Long expectedVersion) {
    }

    @Override
    public Optional<KitchenSettings> settingsOf(UUID ownerId) {
        return jdbc.query("""
                SELECT id, public_slug, display_name, description, operating_state, paused_by, pickup_enabled, version
                FROM kitchen WHERE owner_id = ?""", (rs, n) -> new KitchenSettings(rs.getObject("id", UUID.class), rs.getString("public_slug"),
                rs.getString("display_name"), rs.getString("description"), rs.getString("operating_state"),
                "PAUSED".equals(rs.getString("operating_state")) ? (rs.getString("paused_by") == null ? "STAFF" : rs.getString("paused_by")) : null,
                rs.getBoolean("pickup_enabled"), zoneSettings(rs.getObject("id", UUID.class)), rs.getLong("version")), ownerId)
                .stream().findFirst();
    }

    private List<ZoneSetting> zoneSettings(UUID kitchenId) {
        return jdbc.query("""
                SELECT z.id, z.name_ar, z.name_en, kz.delivery_enabled FROM kitchen_zone kz JOIN service_zone z ON z.id = kz.zone_id
                WHERE kz.kitchen_id = ? ORDER BY z.name_en, z.id""",
                (rs, n) -> new ZoneSetting(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getBoolean(4)), kitchenId);
    }

    /**
     * The cook changes only their own kitchen: description, pickup, delivery among the approved zones, and a
     * pause they control. Suspension, a staff pause and closure cannot be cleared here (BR-009). Approved
     * identity fields (display name, location, contact) need a new application. The kitchen must stay serviceable.
     */
    @Transactional
    public KitchenSettings updateSettings(UUID cookId, SettingsPatch patch, String requestId) {
        KitchenSettings current = jdbc.query("SELECT id FROM kitchen WHERE owner_id = ? FOR UPDATE", (rs, n) -> rs.getObject(1, UUID.class), cookId)
                .stream().findFirst().flatMap(id -> settingsOf(cookId))
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "no approved kitchen"));
        if (patch.expectedVersion() == null || patch.expectedVersion() != current.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale kitchen version");
        }
        List<FieldError> errors = new java.util.ArrayList<>();
        String description = patch.description() == null ? current.description() : patch.description().strip();
        if (description.isEmpty() || description.length() > 2000) {
            errors.add(new FieldError("description", "LENGTH_1_2000"));
        }
        boolean pickup = patch.pickupEnabled() == null ? current.pickupEnabled() : patch.pickupEnabled();
        Map<UUID, Boolean> zones = new java.util.LinkedHashMap<>();
        current.zones().forEach(z -> zones.put(z.zoneId(), z.deliveryEnabled()));
        if (patch.deliveryZones() != null) {
            for (Map.Entry<UUID, Boolean> z : patch.deliveryZones().entrySet()) {
                if (!zones.containsKey(z.getKey()) || z.getValue() == null) {
                    errors.add(new FieldError("deliveryZones", "ZONE_NOT_APPROVED"));
                    break;
                }
                zones.put(z.getKey(), z.getValue());
            }
        }
        if (!pickup && !zones.containsValue(Boolean.TRUE)) {
            errors.add(new FieldError("pickupEnabled", "PICKUP_OR_ZONE_REQUIRED"));
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "invalid kitchen settings", errors);
        }
        String state = current.operatingState();
        String pausedBy = current.pausedBy();
        if (patch.paused() != null) {
            // Pause: ACTIVE -> PAUSED (or already paused). Resume: only a cook pause; ACTIVE stays ACTIVE.
            boolean allowed = patch.paused() ? "ACTIVE".equals(state) || "PAUSED".equals(state)
                    : "ACTIVE".equals(state) || ("PAUSED".equals(state) && "COOK".equals(pausedBy));
            if (!allowed) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "kitchen is " + state + ("PAUSED".equals(state) ? " by staff" : ""),
                        List.of(new FieldError("operatingState", state)));
            }
            if (patch.paused() && "ACTIVE".equals(state)) {
                state = "PAUSED";
                pausedBy = "COOK";
            } else if (!patch.paused() && "PAUSED".equals(state)) {
                state = "ACTIVE";
                pausedBy = null;
            }
        }
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE kitchen SET description = ?, pickup_enabled = ?, operating_state = ?, paused_by = ?,
                       state_reason = CASE WHEN ? THEN NULL ELSE state_reason END, updated_at = ?, version = version + 1 WHERE id = ?""",
                description, pickup, state, pausedBy, !state.equals(current.operatingState()), Timestamp.from(now), current.kitchenId());
        zones.forEach((zone, enabled) -> jdbc.update("""
                UPDATE kitchen_zone SET delivery_enabled = ?, version = version + 1 WHERE kitchen_id = ? AND zone_id = ? AND delivery_enabled <> ?""",
                enabled, current.kitchenId(), zone, enabled));
        audit.record(new Entry(cookId, "kitchen.settings", null, "kitchen", current.kitchenId().toString(), Outcome.ALLOWED, null, requestId,
                Map.of("operatingState", state, "pickupEnabled", pickup)));
        return settingsOf(cookId).orElseThrow();
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
