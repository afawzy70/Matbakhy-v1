package com.matbakhy.catalog.internal.discovery;

import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** API-002: active service zones for the zone selector (localized names), stable order by zone code. */
@Service
public class ZoneService {

    public record Money(long amountMinor, String currency) {
    }

    public record Zone(UUID id, String code, String name, String city, Money deliveryFee) {
    }

    public record ZonePage(List<Zone> items, String nextCursor, boolean hasMore) {
    }

    private final JdbcTemplate jdbc;

    ZoneService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ZonePage activeZones(String cursor, int limit, boolean english) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "limit", List.of(new FieldError("limit", "RANGE_1_100")));
        }
        String after = "";
        if (cursor != null) {
            try {
                after = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
            } catch (RuntimeException e) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "cursor", List.of(new FieldError("cursor", "INVALID")));
            }
        }
        List<Zone> rows = jdbc.query("""
                SELECT id, code, name_ar, name_en, city_ar, city_en, delivery_fee_minor FROM service_zone
                WHERE active AND code > ? ORDER BY code LIMIT ?""",
                (rs, n) -> new Zone(rs.getObject("id", UUID.class), rs.getString("code"), rs.getString(english ? "name_en" : "name_ar"),
                        rs.getString(english ? "city_en" : "city_ar"), new Money(rs.getLong("delivery_fee_minor"), "EGP")),
                after, limit + 1);
        boolean more = rows.size() > limit;
        List<Zone> page = more ? rows.subList(0, limit) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(page.getLast().code().getBytes(StandardCharsets.UTF_8)) : null;
        return new ZonePage(page, next, more);
    }
}
