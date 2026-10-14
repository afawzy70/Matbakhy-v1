package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.AddressDirectory;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Active (not archived) addresses of the given account only. */
@Component
class JdbcAddressDirectory implements AddressDirectory {

    private final JdbcTemplate jdbc;

    JdbcAddressDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<OwnedAddress> owned(UUID accountId, UUID addressId) {
        return jdbc.query("""
                SELECT id, zone_id, version, recipient_name, phone_e164, governorate, city, street, building, floor, unit, landmark
                FROM address WHERE id = ? AND user_id = ? AND archived_at IS NULL""",
                (rs, n) -> new OwnedAddress(rs.getObject("id", UUID.class), rs.getObject("zone_id", UUID.class), rs.getLong("version"),
                        rs.getString("recipient_name"), rs.getString("phone_e164"), rs.getString("governorate"), rs.getString("city"),
                        rs.getString("street"), rs.getString("building"), rs.getString("floor"), rs.getString("unit"), rs.getString("landmark")),
                addressId, accountId).stream().findFirst();
    }
}
