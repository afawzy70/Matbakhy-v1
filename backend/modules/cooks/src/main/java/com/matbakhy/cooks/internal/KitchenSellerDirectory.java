package com.matbakhy.cooks.internal;

import com.matbakhy.catalog.api.SellerDirectory;
import com.matbakhy.cooks.api.KitchenDirectory.OperatingState;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Exposes approved kitchens to the catalog using the same eligibility rule as {@code KitchenDirectory.canSell}. */
@Component
class KitchenSellerDirectory implements SellerDirectory {

    private static final String SELECT = "SELECT id, owner_id, public_slug, display_name, operating_state, pickup_enabled, "
            + "ARRAY(SELECT zone_id FROM kitchen_zone z WHERE z.kitchen_id = kitchen.id AND z.delivery_enabled) AS zones FROM kitchen WHERE ";

    private final JdbcTemplate jdbc;

    KitchenSellerDirectory(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<Seller> byOwner(UUID ownerId) {
        return find("owner_id = ?", ownerId);
    }

    @Override
    public Optional<Seller> byKitchen(UUID kitchenId) {
        return find("id = ?", kitchenId);
    }

    private Optional<Seller> find(String where, UUID id) {
        return jdbc.query(SELECT + where, (rs, n) -> new Seller(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                rs.getString("public_slug"), rs.getString("display_name"),
                new com.matbakhy.cooks.api.KitchenDirectory.KitchenStatus(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                        OperatingState.valueOf(rs.getString("operating_state"))).canSell(), rs.getBoolean("pickup_enabled"),
                java.util.Set.copyOf(CookApplications.uuids(rs.getArray("zones")))), id).stream().findFirst();
    }
}
