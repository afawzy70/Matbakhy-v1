package com.matbakhy.app.support;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Synthetic commerce fixtures for quote/order tests (policy, zones, published meals, open slots, addresses). */
public final class TestCommerce {

    public static final String TERMS = "synthetic-terms-v1";
    public static final String CANCELLATION = "synthetic-cancel-v1";
    private static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public TestCommerce(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    public int activatePolicy(int taxBps) {
        jdbc.update("UPDATE commerce_policy SET status = 'RETIRED' WHERE status = 'ACTIVE'");
        Integer next = jdbc.queryForObject("SELECT coalesce(max(version), 0) + 1 FROM commerce_policy", Integer.class);
        jdbc.update("""
                INSERT INTO commerce_policy (version, label, synthetic, status, tax_rate_bps, commission_rate_bps, max_item_quantity,
                                             quote_ttl_seconds, acceptance_window_minutes, min_acceptance_minutes, cancellation_policy_version,
                                             terms_version)
                VALUES (?, 'SYNTHETIC test policy', true, 'ACTIVE', ?, 1000, 10, 300, 15, 5, ?, ?)""", next, taxBps, CANCELLATION, TERMS);
        return next;
    }

    public void verifyPhone(UUID account) {
        jdbc.update("UPDATE app_user SET phone_e164 = '+201001112233', phone_verified_at = now() WHERE id = ?", account);
    }

    public UUID zone(long fee) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                + "VALUES (?, ?, 'منطقة', 'Zone', 'مدينة', 'City', true, ?)", id, "z-" + id.toString().substring(0, 8), fee);
        return id;
    }

    public void deliversTo(UUID kitchen, UUID zone) {
        jdbc.update("INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES (?, ?)", kitchen, zone);
    }

    /** A published meal with revision 1 and a realistic snapshot. */
    public UUID meal(UUID kitchenId, long price, String titleAr, String titleEn) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO meal (id, kitchen_id, status, title_ar, title_en, description_ar, portion_ar, ingredients_ar, allergen_declaration,
                                  allergen_codes, price_minor, published_at)
                VALUES (?, ?, 'PUBLISHED', ?, ?, 'وصف تجريبي للوجبة', 'علبة', 'مكونات', 'CONTAINS', '{GLUTEN}', ?, now())""",
                id, kitchenId, titleAr, titleEn, price);
        String snapshot = """
                {"titleAr":"%s","titleEn":%s,"allergenDeclaration":"CONTAINS","allergenCodes":["GLUTEN"],"priceMinor":%d,"currency":"EGP"}"""
                .formatted(titleAr, titleEn == null ? "null" : "\"" + titleEn + "\"", price);
        jdbc.update("INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor) VALUES (?, 1, ?::jsonb, ?)", id, snapshot, price);
        return id;
    }

    public UUID slot(UUID kitchenId, int minutesAhead, int maxPortions, Object... mealCapacity) {
        ZonedDateTime start = ZonedDateTime.ofInstant(clock.instant().plus(Duration.ofMinutes(minutesAhead)), CAIRO).withSecond(0).withNano(0);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, state)
                VALUES (?, ?, ?, ?, ?, 120, ?, ?, 'OPEN')""",
                id, kitchenId, start.toLocalDate(), Timestamp.from(start.toInstant()), Timestamp.from(start.plusMinutes(30).toInstant()),
                Timestamp.from(start.minusMinutes(120).toInstant()), maxPortions);
        for (int i = 0; i < mealCapacity.length; i += 2) {
            jdbc.update("INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity) VALUES (?, ?, ?, ?)", UUID.randomUUID(), id,
                    mealCapacity[i], mealCapacity[i + 1]);
        }
        return id;
    }

    public UUID address(UUID owner, UUID zoneId) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO address (id, user_id, label, recipient_name, phone_e164, governorate, city, zone_id, street, building)
                VALUES (?, ?, 'Home', 'Synthetic Recipient', '+201001112233', 'Cairo', 'Cairo', ?, 'Synthetic street 7', '1')""", id, owner, zoneId);
        return id;
    }
}
