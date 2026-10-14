package com.matbakhy.catalog.internal;

import com.matbakhy.catalog.api.ZoneAdministration;
import com.matbakhy.identity.api.ServiceZoneLookup;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Service zones are catalogue/serviceability reference data (list-based zones, MVP data model). */
@Component
class JdbcServiceZones implements ServiceZoneLookup, ZoneAdministration {

    private final JdbcTemplate jdbc;

    JdbcServiceZones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActive(UUID zoneId) {
        Boolean active = jdbc.query("SELECT active FROM service_zone WHERE id = ?", rs -> rs.next() ? rs.getBoolean(1) : null, zoneId);
        return Boolean.TRUE.equals(active);
    }

    private static final String COLUMNS = "id, code, name_ar, name_en, active, delivery_fee_minor, version";

    private static Zone map(java.sql.ResultSet rs, int n) throws java.sql.SQLException {
        return new Zone(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5), rs.getLong(6), rs.getLong(7));
    }

    @Override
    public List<Zone> zones() {
        return jdbc.query("SELECT " + COLUMNS + " FROM service_zone ORDER BY code", JdbcServiceZones::map);
    }

    @Override
    public Optional<Zone> zone(UUID id) {
        return jdbc.query("SELECT " + COLUMNS + " FROM service_zone WHERE id = ?", JdbcServiceZones::map, id).stream().findFirst();
    }

    @Override
    public Optional<Zone> update(UUID id, long expectedVersion, boolean active, long deliveryFeeMinor) {
        int n = jdbc.update("UPDATE service_zone SET active = ?, delivery_fee_minor = ?, version = version + 1 WHERE id = ? AND version = ?",
                active, deliveryFeeMinor, id, expectedVersion);
        return n == 1 ? zone(id) : Optional.empty();
    }
}
