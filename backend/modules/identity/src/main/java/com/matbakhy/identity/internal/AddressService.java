package com.matbakhy.identity.internal;

import com.matbakhy.identity.api.EgyptianPhone;
import com.matbakhy.identity.api.ServiceZoneLookup;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Customer delivery addresses (BR-005). Every query is scoped to the owning account; another account's
 * address is indistinguishable from a missing one (404). The service zone is authoritative; optional
 * coordinates are stored for the cook's convenience and never used to infer or override the zone.
 */
@Service
public class AddressService {

    static final int MAX_ADDRESSES = 20;
    private static final String COLUMNS = "id, label, recipient_name, phone_e164, governorate, city, zone_id, street, building, "
            + "floor, unit, landmark, latitude, longitude, version";

    public record Address(UUID id, String label, String recipientName, String phoneE164, String governorate, String city,
                          UUID zoneId, String street, String building, String floor, String unit, String landmark,
                          BigDecimal latitude, BigDecimal longitude, long version) {
    }

    public record AddressInput(String label, String recipientName, String phone, String governorate, String city, UUID zoneId,
                               String street, String building, String floor, String unit, String landmark,
                               BigDecimal latitude, BigDecimal longitude) {
    }

    private final JdbcTemplate jdbc;
    private final ServiceZoneLookup zones;

    AddressService(JdbcTemplate jdbc, ServiceZoneLookup zones) {
        this.jdbc = jdbc;
        this.zones = zones;
    }

    List<Address> list(UUID accountId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM address WHERE user_id = ? AND archived_at IS NULL ORDER BY created_at, id",
                AddressService::map, accountId);
    }

    @Transactional
    Address create(UUID accountId, AddressInput in) {
        jdbc.queryForObject("SELECT id FROM app_user WHERE id = ? FOR UPDATE", UUID.class, accountId);
        Integer count = jdbc.queryForObject("SELECT count(*) FROM address WHERE user_id = ? AND archived_at IS NULL",
                Integer.class, accountId);
        if (count != null && count >= MAX_ADDRESSES) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "address limit", List.of(new FieldError("addresses", "MAX_20")));
        }
        String phone = validate(in);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO address (id, user_id, label, recipient_name, phone_e164, governorate, city, zone_id, street, building,
                                     floor, unit, landmark, latitude, longitude)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, accountId, in.label(), in.recipientName(), phone, in.governorate(), in.city(), in.zoneId(), in.street(),
                in.building(), in.floor(), in.unit(), in.landmark(), in.latitude(), in.longitude());
        return get(accountId, id);
    }

    @Transactional
    Address update(UUID accountId, UUID addressId, long expectedVersion, AddressInput in) {
        Address current = get(accountId, addressId);
        AddressInput merged = new AddressInput(
                pick(in.label(), current.label()), pick(in.recipientName(), current.recipientName()),
                pick(in.phone(), current.phoneE164()), pick(in.governorate(), current.governorate()), pick(in.city(), current.city()),
                pick(in.zoneId(), current.zoneId()), pick(in.street(), current.street()), pick(in.building(), current.building()),
                pick(in.floor(), current.floor()), pick(in.unit(), current.unit()), pick(in.landmark(), current.landmark()),
                in.latitude() != null || in.longitude() != null ? in.latitude() : current.latitude(),
                in.latitude() != null || in.longitude() != null ? in.longitude() : current.longitude());
        String phone = validate(merged);
        int updated = jdbc.update("""
                UPDATE address SET label = ?, recipient_name = ?, phone_e164 = ?, governorate = ?, city = ?, zone_id = ?, street = ?,
                       building = ?, floor = ?, unit = ?, landmark = ?, latitude = ?, longitude = ?, version = version + 1, updated_at = now()
                WHERE id = ? AND user_id = ? AND archived_at IS NULL AND version = ?""",
                merged.label(), merged.recipientName(), phone, merged.governorate(), merged.city(), merged.zoneId(), merged.street(),
                merged.building(), merged.floor(), merged.unit(), merged.landmark(), merged.latitude(), merged.longitude(),
                addressId, accountId, expectedVersion);
        if (updated == 0) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "address version conflict");
        }
        return get(accountId, addressId);
    }

    /** Soft archive so historical order snapshots that copied this address remain intact. */
    @Transactional
    void archive(UUID accountId, UUID addressId) {
        if (jdbc.update("UPDATE address SET archived_at = now(), version = version + 1 WHERE id = ? AND user_id = ? AND archived_at IS NULL",
                addressId, accountId) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "address not found for caller");
        }
    }

    Address get(UUID accountId, UUID addressId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM address WHERE id = ? AND user_id = ? AND archived_at IS NULL",
                        AddressService::map, addressId, accountId)
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "address not found for caller"));
    }

    private String validate(AddressInput in) {
        if (in.zoneId() == null || !zones.isActive(in.zoneId())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "zone not active", List.of(new FieldError("zoneId", "ZONE_NOT_SERVED")));
        }
        if ((in.latitude() == null) != (in.longitude() == null)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "coordinates pair", List.of(new FieldError("latitude", "PAIR_REQUIRED")));
        }
        return EgyptianPhone.normalize(in.phone()).orElseThrow(() -> new ApiException(ErrorCode.VALIDATION_FAILED,
                "address phone", List.of(new FieldError("phone", "EGYPTIAN_MOBILE"))));
    }

    private static <T> T pick(T value, T fallback) {
        return value != null ? value : fallback;
    }

    static Address map(ResultSet rs, int row) throws SQLException {
        return new Address(rs.getObject("id", UUID.class), rs.getString("label"), rs.getString("recipient_name"),
                rs.getString("phone_e164"), rs.getString("governorate"), rs.getString("city"), rs.getObject("zone_id", UUID.class),
                rs.getString("street"), rs.getString("building"), rs.getString("floor"), rs.getString("unit"), rs.getString("landmark"),
                rs.getBigDecimal("latitude"), rs.getBigDecimal("longitude"), rs.getLong("version"));
    }
}
