package com.matbakhy.catalog.internal;

import com.matbakhy.catalog.api.MealCatalog;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
class JdbcMealCatalog implements MealCatalog {

    private final JdbcTemplate jdbc;

    JdbcMealCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<PricedMeal> current(UUID mealId) {
        return jdbc.query("SELECT id, kitchen_id, status, moderation_state, revision, price_minor, title_ar, title_en FROM meal WHERE id = ?",
                (rs, n) -> new PricedMeal(rs.getObject("id", UUID.class), rs.getObject("kitchen_id", UUID.class),
                        "PUBLISHED".equals(rs.getString("status")) && "VISIBLE".equals(rs.getString("moderation_state")), rs.getInt("revision"), rs.getLong("price_minor"), rs.getString("title_ar"),
                        rs.getString("title_en")), mealId).stream().findFirst();
    }

    @Override
    public Optional<String> revisionSnapshot(UUID mealId, int revision) {
        return jdbc.query("SELECT snapshot::text FROM meal_revision WHERE meal_id = ? AND revision = ?", (rs, n) -> rs.getString(1), mealId,
                revision).stream().findFirst();
    }

    @Override
    public OptionalLong deliveryFee(UUID kitchenId, UUID zoneId) {
        Long fee = jdbc.query("""
                SELECT coalesce(kz.fee_override_minor, z.delivery_fee_minor) FROM kitchen_zone kz JOIN service_zone z ON z.id = kz.zone_id
                WHERE kz.kitchen_id = ? AND kz.zone_id = ? AND kz.delivery_enabled AND z.active""",
                rs -> rs.next() ? rs.getLong(1) : null, kitchenId, zoneId);
        return fee == null ? OptionalLong.empty() : OptionalLong.of(fee);
    }
}
