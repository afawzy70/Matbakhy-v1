package com.matbakhy.cooks.internal;

import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Row access for cook applications and their immutable revisions. Callers enforce scope and transitions. */
@Component
public class CookApplications {

    public record Draft(String publicName, String bio, String contactPhone, String privateLocation, boolean pickupEnabled,
                        List<UUID> zoneIds) {
    }

    public record Application(UUID id, UUID userId, String state, Draft draft, int currentRevision, Instant createdAt,
                              Instant updatedAt, long version) {
    }

    public record Revision(int revision, String publicName, String bio, String contactPhone, String privateLocation,
                           boolean pickupEnabled, List<UUID> zoneIds, Instant submittedAt, String decision, String decisionReason,
                           UUID reviewerId, Instant decidedAt) {
    }

    private static final String COLUMNS = "id, user_id, state, public_name, bio, contact_phone, private_location, pickup_enabled, "
            + "zone_ids, current_revision, created_at, updated_at, version";

    private final JdbcTemplate jdbc;

    CookApplications(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    Optional<Application> byUser(UUID userId, boolean lock) {
        return jdbc.query("SELECT " + COLUMNS + " FROM cook_application WHERE user_id = ?" + (lock ? " FOR UPDATE" : ""),
                CookApplications::map, userId).stream().findFirst();
    }

    Optional<Application> byId(UUID id, boolean lock) {
        return jdbc.query("SELECT " + COLUMNS + " FROM cook_application WHERE id = ?" + (lock ? " FOR UPDATE" : ""),
                CookApplications::map, id).stream().findFirst();
    }

    List<Revision> revisions(UUID applicationId) {
        return jdbc.query("""
                SELECT revision, public_name, bio, contact_phone, private_location, pickup_enabled, zone_ids, submitted_at,
                       decision, decision_reason, reviewer_id, decided_at
                FROM cook_application_revision WHERE application_id = ? ORDER BY revision""",
                (rs, n) -> new Revision(rs.getInt("revision"), rs.getString("public_name"), rs.getString("bio"),
                        rs.getString("contact_phone"), rs.getString("private_location"), rs.getBoolean("pickup_enabled"),
                        uuids(rs.getArray("zone_ids")), instant(rs, "submitted_at"), rs.getString("decision"),
                        rs.getString("decision_reason"), rs.getObject("reviewer_id", UUID.class), instant(rs, "decided_at")),
                applicationId);
    }

    static String uuidArray(List<UUID> ids) {
        return ids.stream().map(UUID::toString).collect(Collectors.joining(",", "{", "}"));
    }

    static List<UUID> uuids(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        Object[] values = (Object[]) array.getArray();
        return Arrays.stream(values).map(v -> v instanceof UUID u ? u : UUID.fromString(v.toString())).toList();
    }

    static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static Application map(ResultSet rs, int n) throws SQLException {
        return new Application(rs.getObject("id", UUID.class), rs.getObject("user_id", UUID.class), rs.getString("state"),
                new Draft(rs.getString("public_name"), rs.getString("bio"), rs.getString("contact_phone"),
                        rs.getString("private_location"), rs.getBoolean("pickup_enabled"), uuids(rs.getArray("zone_ids"))),
                rs.getInt("current_revision"), instant(rs, "created_at"), instant(rs, "updated_at"), rs.getLong("version"));
    }
}
