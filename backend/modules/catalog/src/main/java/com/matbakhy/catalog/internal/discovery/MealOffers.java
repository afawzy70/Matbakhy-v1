package com.matbakhy.catalog.internal.discovery;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Next orderable slots and availability of meals (read model over the availability tables). Orderable means the
 * same as {@code SlotDirectory.SlotStatus.orderableAt}: slot OPEN, before cutoff by server time, meal offered.
 * Remaining portions are bounded by the meal capacity and by the slot aggregate (BR-013).
 */
@Component
public class MealOffers {

    public static final int MAX_NEXT_SLOTS = 5;

    public record SlotOffer(UUID id, Instant startsAt, Instant endsAt, String timezone, Instant cutoffAt, int availablePortions,
                            List<String> fulfillmentModes) {
    }

    public record Offer(List<SlotOffer> nextSlots, String availability) {
    }

    /** SQL predicate on aliases {@code s} (cook_slot) and {@code ms} (meal_slot); one parameter: now. */
    public static final String ORDERABLE = "s.state = 'OPEN' AND s.cutoff_at > ? AND ms.capacity > 0";

    private final JdbcTemplate jdbc;
    private final Clock clock;

    MealOffers(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Offers per meal, optionally restricted to a local date or one slot; modes are the kitchen's per meal. */
    public Map<UUID, Offer> offers(Collection<UUID> mealIds, Map<UUID, List<String>> modesByMeal, LocalDate date, UUID slotId) {
        Map<UUID, List<SlotOffer>> slots = new HashMap<>();
        if (!mealIds.isEmpty()) {
            jdbc.query("""
                    SELECT * FROM (
                        SELECT ms.meal_id, s.id, s.starts_at, s.ends_at, s.cutoff_at,
                               GREATEST(0, LEAST(ms.capacity - ms.reserved - ms.allocated - ms.consumed,
                                                 s.max_portions - s.reserved - s.allocated - s.consumed)) AS available,
                               row_number() OVER (PARTITION BY ms.meal_id ORDER BY s.starts_at, s.id) AS rn
                        FROM meal_slot ms JOIN cook_slot s ON s.id = ms.cook_slot_id
                        WHERE ms.meal_id = ANY (?)""" + " AND " + ORDERABLE + """
                          AND (?::date IS NULL OR s.local_date = ?) AND (?::uuid IS NULL OR s.id = ?)
                    ) t WHERE rn <= ? ORDER BY meal_id, starts_at, id""",
                    rs -> {
                        UUID meal = rs.getObject("meal_id", UUID.class);
                        slots.computeIfAbsent(meal, m -> new ArrayList<>()).add(new SlotOffer(rs.getObject("id", UUID.class),
                                rs.getTimestamp("starts_at").toInstant(), rs.getTimestamp("ends_at").toInstant(), "Africa/Cairo",
                                rs.getTimestamp("cutoff_at").toInstant(), rs.getInt("available"), modesByMeal.getOrDefault(meal, List.of())));
                    },
                    mealIds.toArray(UUID[]::new), Timestamp.from(clock.instant()), date, date, slotId, slotId, MAX_NEXT_SLOTS);
        }
        Map<UUID, Offer> out = new HashMap<>();
        for (UUID meal : mealIds) {
            List<SlotOffer> next = slots.getOrDefault(meal, List.of());
            String availability = next.isEmpty() ? "UNAVAILABLE" : next.stream().anyMatch(o -> o.availablePortions() > 0) ? "AVAILABLE" : "SOLD_OUT";
            out.put(meal, new Offer(List.copyOf(next), availability));
        }
        return out;
    }
}
