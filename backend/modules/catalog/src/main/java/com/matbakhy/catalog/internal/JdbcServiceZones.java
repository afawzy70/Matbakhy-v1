package com.matbakhy.catalog.internal;

import com.matbakhy.identity.api.ServiceZoneLookup;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Service zones are catalogue/serviceability reference data (list-based zones, MVP data model). */
@Component
class JdbcServiceZones implements ServiceZoneLookup {

    private final JdbcTemplate jdbc;

    JdbcServiceZones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean isActive(UUID zoneId) {
        Boolean active = jdbc.query("SELECT active FROM service_zone WHERE id = ?", rs -> rs.next() ? rs.getBoolean(1) : null, zoneId);
        return Boolean.TRUE.equals(active);
    }
}
