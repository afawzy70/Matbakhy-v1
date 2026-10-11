package com.matbakhy.catalog.internal;

import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * API-018..020 (BR-040): idempotent per user and meal. Favorites of meals that are no longer orderable stay in the list
 * annotated UNAVAILABLE (paused, archived, hidden or kitchen not selling) instead of disappearing.
 */
@Service
public class FavoriteService {

    public record Favorite(UUID mealId, String title, Long priceMinor, String kitchenName, boolean available, Instant createdAt) {
    }

    public record FavoritePage(List<Favorite> items, String nextCursor, boolean hasMore) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    FavoriteService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    private static final String SELECT = """
            SELECT f.meal_id, CASE WHEN ? THEN coalesce(m.title_en, m.title_ar) ELSE m.title_ar END, m.price_minor, k.display_name,
                   (m.status = 'PUBLISHED' AND m.moderation_state = 'VISIBLE' AND k.operating_state = 'ACTIVE') AS available, f.created_at
            FROM favorite f JOIN meal m ON m.id = f.meal_id JOIN kitchen k ON k.id = m.kitchen_id""";

    public FavoritePage list(UUID userId, boolean english, int limit) {
        if (limit < 1 || limit > 100) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "invalid limit", List.of(new FieldError("limit", "RANGE_1_100")));
        }
        List<Favorite> rows = jdbc.query(SELECT + " WHERE f.user_id = ? ORDER BY f.created_at DESC, f.meal_id LIMIT ?", (rs, n) -> new Favorite(
                rs.getObject(1, UUID.class), rs.getString(2), rs.getObject(3) == null ? null : rs.getLong(3), rs.getString(4), rs.getBoolean(5),
                rs.getTimestamp(6).toInstant()), english, userId, limit + 1);
        boolean more = rows.size() > limit;
        return new FavoritePage(more ? rows.subList(0, limit) : rows, null, more);
    }

    /** Only a meal that is currently public can be added; repeating the call returns the same favorite. */
    public Favorite add(UUID userId, UUID mealId, boolean english) {
        Boolean visible = jdbc.query("""
                SELECT m.status = 'PUBLISHED' AND m.moderation_state = 'VISIBLE' AND k.operating_state = 'ACTIVE' FROM meal m
                JOIN kitchen k ON k.id = m.kitchen_id WHERE m.id = ?""", rs -> rs.next() ? rs.getBoolean(1) : null, mealId);
        boolean exists = Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM favorite WHERE user_id = ? AND meal_id = ?)", Boolean.class,
                userId, mealId));
        if (!exists && !Boolean.TRUE.equals(visible)) {
            throw new ApiException(ErrorCode.NOT_FOUND, "meal not public");
        }
        jdbc.update("INSERT INTO favorite (user_id, meal_id, created_at) VALUES (?, ?, ?) ON CONFLICT DO NOTHING", userId, mealId,
                Timestamp.from(clock.instant()));
        return jdbc.query(SELECT + " WHERE f.user_id = ? AND f.meal_id = ?", (rs, n) -> new Favorite(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3) == null ? null : rs.getLong(3), rs.getString(4), rs.getBoolean(5), rs.getTimestamp(6).toInstant()), english, userId, mealId)
                .getFirst();
    }

    public void remove(UUID userId, UUID mealId) {
        jdbc.update("DELETE FROM favorite WHERE user_id = ? AND meal_id = ?", userId, mealId);
    }
}
