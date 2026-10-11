package com.matbakhy.availability.internal;

import com.matbakhy.availability.api.SlotDirectory;
import com.matbakhy.availability.api.SlotParticipants;
import com.matbakhy.availability.api.SlotParticipants.Kitchen;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dated slots and capacity (BR-013..BR-015). Times are scheduled in Africa/Cairo and stored as UTC instants;
 * cutoff = start − lead time. Capacity can never be set below what orders already hold (reserved + allocated +
 * consumed) at either the meal or the slot level; the database enforces the same bound.
 */
@Service
public class SlotService implements SlotDirectory {

    static final int DEFAULT_LEAD_MINUTES = 120;
    static final int MAX_DAYS_AHEAD = 60;
    private static final Set<String> FINAL = Set.of("CLOSED", "CANCELLED");

    public record MealCapacity(UUID mealId, int capacity) {
    }

    public record CreateSlot(LocalDate localDate, LocalTime localStart, LocalTime localEnd, String timezone, String startOffset,
                             String endOffset, int maxPortions, Integer leadMinutes, List<MealCapacity> mealCapacities) {
    }

    public record UpdateSlot(Integer maxPortions, List<MealCapacity> mealCapacities, String state) {
    }

    public record MealSlot(UUID mealId, int capacity, int reserved, int allocated, int consumed, int available) {
    }

    public record CookSlot(UUID id, UUID kitchenId, LocalDate localDate, Instant startsAt, Instant endsAt, String timezone,
                           int leadMinutes, Instant cutoffAt, int maxPortions, int reserved, int allocated, int consumed,
                           int availablePortions, String state, boolean orderable, List<MealSlot> meals, long version) {
    }

    public record CookSlotPage(List<CookSlot> items, String nextCursor, boolean hasMore) {
    }

    public record SlotAvailability(UUID id, Instant startsAt, Instant endsAt, String timezone, Instant cutoffAt, int availablePortions,
                                   List<String> fulfillmentModes) {
    }

    public record SlotPage(List<SlotAvailability> items, String nextCursor, boolean hasMore) {
    }

    private final JdbcTemplate jdbc;
    private final SlotParticipants participants;
    private final Clock clock;

    SlotService(JdbcTemplate jdbc, SlotParticipants participants, Clock clock) {
        this.jdbc = jdbc;
        this.participants = participants;
        this.clock = clock;
    }

    // ---- cook side ----

    @Transactional
    public CookSlot create(UUID actorId, CreateSlot in) {
        Kitchen kitchen = requireKitchen(actorId);
        if (in.timezone() != null && !in.timezone().equals(CairoTime.ZONE.getId())) {
            throw invalid("timezone", "ONLY_AFRICA_CAIRO");
        }
        if (in.localDate() == null || in.localStart() == null || in.localEnd() == null) {
            throw invalid("localDate", "REQUIRED");
        }
        Instant start = CairoTime.resolve(in.localDate(), in.localStart(), in.startOffset(), "localStart");
        Instant end = CairoTime.resolve(in.localDate(), in.localEnd(), in.endOffset(), "localEnd");
        if (!end.isAfter(start)) {
            throw invalid("localEnd", "END_NOT_AFTER_START");
        }
        int lead = in.leadMinutes() == null ? DEFAULT_LEAD_MINUTES : in.leadMinutes();
        if (lead < 30 || lead > 1440) {
            throw invalid("leadMinutes", "RANGE_30_1440");
        }
        if (in.maxPortions() < 1 || in.maxPortions() > 500) {
            throw invalid("maxPortions", "RANGE_1_500");
        }
        Instant now = clock.instant();
        Instant cutoff = start.minus(Duration.ofMinutes(lead));
        if (!cutoff.isAfter(now)) {
            throw new ApiException(ErrorCode.SLOT_CLOSED, "cutoff already passed", List.of(new FieldError("localStart", "CUTOFF_PASSED")));
        }
        if (start.isAfter(now.plus(Duration.ofDays(MAX_DAYS_AHEAD)))) {
            throw invalid("localDate", "MAX_60_DAYS_AHEAD");
        }
        List<MealCapacity> meals = in.mealCapacities() == null ? List.of() : in.mealCapacities();
        validateMeals(kitchen, meals, in.maxPortions());
        lockKitchen(kitchen.kitchenId());
        Boolean overlap = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM cook_slot WHERE kitchen_id = ? AND state <> 'CANCELLED' AND starts_at < ? AND ends_at > ?)""",
                Boolean.class, kitchen.kitchenId(), Timestamp.from(end), Timestamp.from(start));
        if (Boolean.TRUE.equals(overlap)) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "overlapping slot",
                    List.of(new FieldError("localStart", "OVERLAPS_EXISTING_SLOT")));
        }
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                id, kitchen.kitchenId(), in.localDate(), Timestamp.from(start), Timestamp.from(end), lead, Timestamp.from(cutoff),
                in.maxPortions(), Timestamp.from(now), Timestamp.from(now));
        for (MealCapacity m : meals) {
            jdbc.update("INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity) VALUES (?, ?, ?, ?)", UUID.randomUUID(), id, m.mealId(),
                    m.capacity());
        }
        return cookSlot(id);
    }

    @Transactional
    public CookSlot update(UUID actorId, UUID slotId, Long expectedVersion, UpdateSlot in) {
        Kitchen kitchen = requireKitchen(actorId);
        Row slot = lockOwn(kitchen, slotId);
        if (expectedVersion == null) {
            throw invalid("expectedVersion", "REQUIRED");
        }
        if (expectedVersion != slot.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale slot version");
        }
        if (FINAL.contains(slot.state())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "slot is " + slot.state());
        }
        Instant now = clock.instant();
        boolean pastCutoff = !now.isBefore(slot.cutoffAt());
        boolean capacityChange = in.maxPortions() != null || in.mealCapacities() != null;
        if (capacityChange && pastCutoff) {
            throw new ApiException(ErrorCode.SLOT_CLOSED, "capacity cannot change after cutoff");
        }
        int maxPortions = in.maxPortions() != null ? in.maxPortions() : slot.maxPortions();
        if (in.maxPortions() != null) {
            if (maxPortions < 1 || maxPortions > 500) {
                throw invalid("maxPortions", "RANGE_1_500");
            }
            if (maxPortions < slot.used()) {
                throw new ApiException(ErrorCode.STATE_CONFLICT, "capacity below obligations",
                        List.of(new FieldError("maxPortions", "BELOW_OBLIGATIONS")));
            }
        }
        if (in.mealCapacities() != null) {
            validateMeals(kitchen, in.mealCapacities(), maxPortions);
            Map<UUID, int[]> existing = new LinkedHashMap<>();
            jdbc.query("SELECT meal_id, capacity, reserved + allocated + consumed FROM meal_slot WHERE cook_slot_id = ? FOR UPDATE",
                    rs -> {
                        existing.put(rs.getObject(1, UUID.class), new int[] {rs.getInt(2), rs.getInt(3)});
                    }, slotId);
            Set<UUID> keep = new HashSet<>();
            for (MealCapacity m : in.mealCapacities()) {
                keep.add(m.mealId());
                int[] current = existing.get(m.mealId());
                if (current == null) {
                    jdbc.update("INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity) VALUES (?, ?, ?, ?)", UUID.randomUUID(), slotId,
                            m.mealId(), m.capacity());
                } else if (current[0] != m.capacity()) {
                    if (m.capacity() < current[1]) {
                        throw new ApiException(ErrorCode.STATE_CONFLICT, "meal capacity below obligations",
                                List.of(new FieldError("mealCapacities", "BELOW_OBLIGATIONS")));
                    }
                    jdbc.update("UPDATE meal_slot SET capacity = ?, version = version + 1 WHERE cook_slot_id = ? AND meal_id = ?", m.capacity(),
                            slotId, m.mealId());
                }
            }
            for (Map.Entry<UUID, int[]> e : existing.entrySet()) {
                if (!keep.contains(e.getKey())) {
                    if (e.getValue()[1] > 0) {
                        throw new ApiException(ErrorCode.STATE_CONFLICT, "meal with obligations removed",
                                List.of(new FieldError("mealCapacities", "BELOW_OBLIGATIONS")));
                    }
                    jdbc.update("DELETE FROM meal_slot WHERE cook_slot_id = ? AND meal_id = ?", slotId, e.getKey());
                }
            }
        }
        String state = slot.state();
        if (in.state() != null && !in.state().equals(slot.state())) {
            state = transition(slot, in.state(), pastCutoff);
        }
        jdbc.update("UPDATE cook_slot SET max_portions = ?, state = ?, updated_at = ?, version = version + 1 WHERE id = ?", maxPortions, state,
                Timestamp.from(now), slotId);
        return cookSlot(slotId);
    }

    private String transition(Row slot, String target, boolean pastCutoff) {
        String from = slot.state();
        boolean allowed = switch (target) {
            case "OPEN" -> from.equals("DRAFT") || from.equals("PAUSED");
            case "PAUSED" -> from.equals("OPEN");
            case "CLOSED" -> from.equals("OPEN") || from.equals("PAUSED");
            case "CANCELLED" -> from.equals("DRAFT") || from.equals("OPEN") || from.equals("PAUSED");
            default -> throw invalid("state", "UNKNOWN_STATE");
        };
        if (!allowed) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, from + " to " + target + " not allowed");
        }
        if (target.equals("OPEN")) {
            if (pastCutoff) {
                throw new ApiException(ErrorCode.SLOT_CLOSED, "cannot open after cutoff");
            }
            Integer offered = jdbc.queryForObject("SELECT count(*) FROM meal_slot WHERE cook_slot_id = ? AND capacity > 0", Integer.class,
                    slot.id());
            if (offered == null || offered == 0) {
                throw invalid("mealCapacities", "MEAL_CAPACITY_REQUIRED");
            }
        }
        if (target.equals("CANCELLED") && slot.used() > 0) {
            // Cancelling a slot with orders needs the ops exception workflow (MB-019/MB-023), not a cook edit.
            throw new ApiException(ErrorCode.STATE_CONFLICT, "slot has order obligations");
        }
        return target;
    }

    public CookSlotPage list(UUID actorId, LocalDate from, LocalDate to, String cursor, int limit) {
        Kitchen kitchen = requireKitchen(actorId);
        LocalDate start = from != null ? from : LocalDate.now(clock.withZone(CairoTime.ZONE));
        LocalDate end = to != null ? to : start.plusDays(MAX_DAYS_AHEAD);
        if (end.isBefore(start) || end.isAfter(start.plusDays(92))) {
            throw invalid("to", "RANGE_MAX_92_DAYS");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        Cursor c = Cursor.parse(cursor);
        List<UUID> ids = jdbc.queryForList("""
                SELECT id FROM cook_slot WHERE kitchen_id = ? AND local_date BETWEEN ? AND ? AND (starts_at, id) > (?, ?)
                ORDER BY starts_at, id LIMIT ?""", UUID.class, kitchen.kitchenId(), start, end, Timestamp.from(c.at()), c.id(), limit + 1);
        boolean more = ids.size() > limit;
        List<CookSlot> items = (more ? ids.subList(0, limit) : ids).stream().map(this::cookSlot).toList();
        return new CookSlotPage(items, more ? Cursor.of(items.getLast().startsAt(), items.getLast().id()) : null, more);
    }

    // ---- public side ----

    /** API-006: future orderable slots of a kitchen that can sell; a hidden kitchen is 404. */
    public SlotPage publicSlots(UUID kitchenId, LocalDate date, String fulfillmentMode, UUID zoneId, String cursor, int limit) {
        Kitchen kitchen = participants.kitchen(kitchenId).filter(Kitchen::canSell)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "kitchen not selling"));
        if (fulfillmentMode != null && !Set.of("PICKUP", "DELIVERY").contains(fulfillmentMode)) {
            throw invalid("fulfillmentMode", "UNKNOWN_MODE");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        List<String> modes = new ArrayList<>();
        if (kitchen.pickupEnabled() && !"DELIVERY".equals(fulfillmentMode)) {
            modes.add("PICKUP");
        }
        boolean delivers = zoneId == null ? !kitchen.deliveryZoneIds().isEmpty() : kitchen.deliveryZoneIds().contains(zoneId);
        if (delivers && !"PICKUP".equals(fulfillmentMode)) {
            modes.add("DELIVERY");
        }
        if (modes.isEmpty()) {
            return new SlotPage(List.of(), null, false);
        }
        Cursor c = Cursor.parse(cursor);
        Instant now = clock.instant();
        List<SlotAvailability> rows = jdbc.query("""
                SELECT id, starts_at, ends_at, cutoff_at, max_portions - reserved - allocated - consumed AS available
                FROM cook_slot WHERE kitchen_id = ? AND state = 'OPEN' AND cutoff_at > ? AND (?::date IS NULL OR local_date = ?)
                  AND (starts_at, id) > (?, ?)
                ORDER BY starts_at, id LIMIT ?""",
                (rs, n) -> new SlotAvailability(rs.getObject("id", UUID.class), instant(rs, "starts_at"), instant(rs, "ends_at"),
                        CairoTime.ZONE.getId(), instant(rs, "cutoff_at"), Math.max(0, rs.getInt("available")), List.copyOf(modes)),
                kitchenId, Timestamp.from(now), date, date, Timestamp.from(c.at()), c.id(), limit + 1);
        boolean more = rows.size() > limit;
        List<SlotAvailability> page = more ? rows.subList(0, limit) : rows;
        return new SlotPage(page, more ? Cursor.of(page.getLast().startsAt(), page.getLast().id()) : null, more);
    }

    @Override
    public Optional<SlotStatus> slot(UUID slotId) {
        return jdbc.query("SELECT id FROM cook_slot WHERE id = ?", (rs, n) -> rs.getObject(1, UUID.class), slotId).stream().findFirst()
                .map(this::cookSlot).map(s -> {
                    Map<UUID, Integer> meals = new LinkedHashMap<>();
                    s.meals().forEach(m -> meals.put(m.mealId(), m.available()));
                    return new SlotStatus(s.id(), s.kitchenId(), s.startsAt(), s.endsAt(), s.cutoffAt(), s.state(), s.availablePortions(),
                            Map.copyOf(meals));
                });
    }

    // ---- internals ----

    private record Row(UUID id, UUID kitchenId, Instant cutoffAt, int maxPortions, int used, String state, long version) {
    }

    private record Cursor(Instant at, UUID id) {
        static Cursor parse(String cursor) {
            if (cursor == null) {
                return new Cursor(Instant.EPOCH, new UUID(0, 0));
            }
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                return new Cursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
            } catch (RuntimeException e) {
                throw invalid("cursor", "INVALID");
            }
        }

        static String of(Instant at, UUID id) {
            return Base64.getUrlEncoder().withoutPadding().encodeToString((at + "|" + id).getBytes(StandardCharsets.UTF_8));
        }
    }

    private Kitchen requireKitchen(UUID actorId) {
        return participants.kitchenOwnedBy(actorId)
                .orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "caller has no approved kitchen"));
    }

    private void lockKitchen(UUID kitchenId) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> null, "cook_slot:" + kitchenId);
    }

    private Row lockOwn(Kitchen kitchen, UUID slotId) {
        return jdbc.query("""
                SELECT id, kitchen_id, cutoff_at, max_portions, reserved + allocated + consumed AS used, state, version
                FROM cook_slot WHERE id = ? AND kitchen_id = ? FOR UPDATE""",
                (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("kitchen_id", UUID.class), instant(rs, "cutoff_at"),
                        rs.getInt("max_portions"), rs.getInt("used"), rs.getString("state"), rs.getLong("version")),
                slotId, kitchen.kitchenId()).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "slot not found"));
    }

    private void validateMeals(Kitchen kitchen, List<MealCapacity> meals, int maxPortions) {
        Set<UUID> seen = new HashSet<>();
        for (MealCapacity m : meals) {
            if (m.mealId() == null || !seen.add(m.mealId())) {
                throw invalid("mealCapacities", "DUPLICATE_OR_MISSING_MEAL");
            }
            if (m.capacity() < 0 || m.capacity() > maxPortions) {
                throw invalid("mealCapacities", "CAPACITY_0_TO_MAX_PORTIONS");
            }
            var meal = participants.meal(m.mealId());
            if (meal.isEmpty() || !meal.get().kitchenId().equals(kitchen.kitchenId()) || meal.get().archived()) {
                throw invalid("mealCapacities", "MEAL_NOT_OFFERABLE");
            }
        }
        if (meals.size() > 50) {
            throw invalid("mealCapacities", "MAX_50");
        }
    }

    private CookSlot cookSlot(UUID id) {
        List<MealSlot> meals = jdbc.query("""
                SELECT meal_id, capacity, reserved, allocated, consumed FROM meal_slot WHERE cook_slot_id = ? ORDER BY meal_id""",
                (rs, n) -> new MealSlot(rs.getObject(1, UUID.class), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                        Math.max(0, rs.getInt(2) - rs.getInt(3) - rs.getInt(4) - rs.getInt(5))), id);
        Instant now = clock.instant();
        return jdbc.query("SELECT * FROM cook_slot WHERE id = ?", (rs, n) -> {
            int available = Math.max(0, rs.getInt("max_portions") - rs.getInt("reserved") - rs.getInt("allocated") - rs.getInt("consumed"));
            List<MealSlot> bounded = meals.stream().map(m -> new MealSlot(m.mealId(), m.capacity(), m.reserved(), m.allocated(), m.consumed(),
                    Math.min(m.available(), available))).toList();
            Instant cutoff = instant(rs, "cutoff_at");
            String state = rs.getString("state");
            return new CookSlot(rs.getObject("id", UUID.class), rs.getObject("kitchen_id", UUID.class), rs.getObject("local_date", LocalDate.class),
                    instant(rs, "starts_at"), instant(rs, "ends_at"), rs.getString("timezone"), rs.getInt("lead_minutes"), cutoff,
                    rs.getInt("max_portions"), rs.getInt("reserved"), rs.getInt("allocated"), rs.getInt("consumed"), available, state,
                    "OPEN".equals(state) && now.isBefore(cutoff), bounded, rs.getLong("version"));
        }, id).getFirst();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
