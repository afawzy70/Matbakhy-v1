package com.matbakhy.catalog.internal;

import com.matbakhy.availability.api.SlotParticipants;
import com.matbakhy.catalog.api.SellerDirectory;
import com.matbakhy.catalog.api.SellerDirectory.Seller;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Supplies kitchens (through the cooks module) and meals to the availability module. */
@Component
class CatalogSlotParticipants implements SlotParticipants {

    private final SellerDirectory sellers;
    private final JdbcTemplate jdbc;

    CatalogSlotParticipants(SellerDirectory sellers, JdbcTemplate jdbc) {
        this.sellers = sellers;
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Kitchen> kitchenOwnedBy(UUID userId) {
        return sellers.byOwner(userId).map(CatalogSlotParticipants::kitchen);
    }

    @Override
    public Optional<Kitchen> kitchen(UUID kitchenId) {
        return sellers.byKitchen(kitchenId).map(CatalogSlotParticipants::kitchen);
    }

    @Override
    public Optional<Meal> meal(UUID mealId) {
        return jdbc.query("SELECT id, kitchen_id, status FROM meal WHERE id = ?",
                (rs, n) -> new Meal(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), "ARCHIVED".equals(rs.getString(3))), mealId)
                .stream().findFirst();
    }

    private static Kitchen kitchen(Seller s) {
        return new Kitchen(s.kitchenId(), s.canSell(), s.pickupEnabled(), s.deliveryZoneIds());
    }
}
