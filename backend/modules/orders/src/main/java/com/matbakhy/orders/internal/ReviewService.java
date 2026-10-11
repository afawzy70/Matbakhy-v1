package com.matbakhy.orders.internal;

import com.matbakhy.catalog.api.KitchenRatings;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verified-order reviews (BR-039): only the order's customer, only COMPLETED orders, once per order, integer 1..5.
 * Only VISIBLE reviews are public and count in ratings. Reports are once per reviewer and review and rate-limited;
 * staff with content.moderate hide or restore reviews with a reason (audited). Public reviews carry no customer data.
 */
@Service
public class ReviewService implements KitchenRatings {

    static final Set<String> REPORT_REASONS = Set.of("OFFENSIVE", "PRIVATE_INFO", "SPAM", "NOT_ABOUT_ORDER", "OTHER");
    static final int REPORTS_PER_HOUR = 20;

    public record Review(UUID id, UUID orderId, UUID kitchenId, int rating, String text, String visibility, Instant createdAt, long version) {
    }

    public record PublicReview(UUID id, int rating, String text, Instant createdAt) {
    }

    public record ReviewPage(List<PublicReview> items, Rating summary, String nextCursor, boolean hasMore) {
    }

    /** Staff moderation queue entry: reports are counted by reason; reporter identities are not exposed. */
    public record QueuedReview(UUID id, UUID orderId, UUID kitchenId, int rating, String text, String visibility, String moderationReason,
                               int reportCount, List<String> reportReasons, Instant createdAt, long version) {
    }

    private final JdbcTemplate jdbc;
    private final AuditLog audit;
    private final Clock clock;

    ReviewService(JdbcTemplate jdbc, AuditLog audit, Clock clock) {
        this.jdbc = jdbc;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional
    public Review create(UUID customerId, UUID orderId, Integer rating, String text) {
        Map<String, Object> o = jdbc.queryForList("SELECT customer_id, kitchen_id, state FROM customer_order WHERE id = ?", orderId).stream()
                .findFirst().filter(r -> customerId.equals(r.get("customer_id"))).orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "order not found"));
        if (!"COMPLETED".equals(o.get("state"))) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "only completed orders can be reviewed", List.of(new FieldError("state", (String) o.get("state"))));
        }
        if (rating == null || rating < 1 || rating > 5) {
            throw invalid("rating", "INTEGER_1_5");
        }
        String body = text == null || text.isBlank() ? null : text.strip();
        if (body != null && body.length() > 1000) {
            throw invalid("text", "MAX_1000");
        }
        UUID id = UUID.randomUUID();
        try {
            jdbc.update("INSERT INTO review (id, order_id, customer_id, kitchen_id, rating, text, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)", id, orderId,
                    customerId, o.get("kitchen_id"), rating, body, Timestamp.from(clock.instant()));
        } catch (DuplicateKeyException e) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "order already reviewed", List.of(new FieldError("orderId", "ALREADY_REVIEWED")));
        }
        return review(id);
    }

    public ReviewPage kitchenReviews(UUID kitchenId, String cursor, int limit) {
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        Instant beforeTime = Instant.parse("9999-12-31T00:00:00Z");
        UUID beforeId = new UUID(-1L, -1L);
        if (cursor != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                beforeTime = Instant.parse(parts[0]);
                beforeId = UUID.fromString(parts[1]);
            } catch (RuntimeException e) {
                throw invalid("cursor", "INVALID");
            }
        }
        List<PublicReview> rows = jdbc.query("""
                SELECT id, rating, text, created_at FROM review WHERE kitchen_id = ? AND visibility = 'VISIBLE' AND (created_at, id) < (?, ?)
                ORDER BY created_at DESC, id DESC LIMIT ?""", (rs, n) -> new PublicReview(rs.getObject(1, UUID.class), rs.getInt(2), rs.getString(3),
                rs.getTimestamp(4).toInstant()), kitchenId, Timestamp.from(beforeTime), beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<PublicReview> page = more ? rows.subList(0, limit) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString((page.getLast().createdAt() + "|" + page.getLast().id())
                .getBytes(StandardCharsets.UTF_8)) : null;
        return new ReviewPage(page, ratings(List.of(kitchenId)).getOrDefault(kitchenId, new Rating(0, null)), next, more);
    }

    @Transactional
    public void report(UUID reporterId, UUID reviewId, String reasonCode, String description) {
        if (jdbc.query("SELECT 1 FROM review WHERE id = ? AND visibility = 'VISIBLE'", rs -> rs.next() ? 1 : null, reviewId) == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "review not found");
        }
        if (reasonCode == null || !REPORT_REASONS.contains(reasonCode)) {
            throw invalid("reasonCode", "ALLOWED_" + String.join("|", REPORT_REASONS.stream().sorted().toList()));
        }
        if (description != null && description.length() > 500) {
            throw invalid("description", "MAX_500");
        }
        Integer recent = jdbc.queryForObject("SELECT count(*) FROM review_report WHERE reporter_id = ? AND created_at > ?", Integer.class, reporterId,
                Timestamp.from(clock.instant().minusSeconds(3600)));
        if (recent >= REPORTS_PER_HOUR) {
            throw new ApiException(ErrorCode.RATE_LIMITED, "too many reports");
        }
        jdbc.update("""
                INSERT INTO review_report (id, review_id, reporter_id, reason_code, description, created_at) VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (review_id, reporter_id) DO NOTHING""", UUID.randomUUID(), reviewId, reporterId, reasonCode,
                description == null || description.isBlank() ? null : description.strip(), Timestamp.from(clock.instant()));
    }

    @Transactional
    public Review moderate(UUID staffId, UUID reviewId, String visibility, String reason, Long expectedVersion, String requestId) {
        Review r = jdbc.query("SELECT id, order_id, kitchen_id, rating, text, visibility, created_at, version FROM review WHERE id = ? FOR UPDATE",
                (rs, n) -> new Review(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getInt(4), rs.getString(5),
                        rs.getString(6), rs.getTimestamp(7).toInstant(), rs.getLong(8)), reviewId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "review not found"));
        if (expectedVersion == null || expectedVersion != r.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale review version");
        }
        if (!Set.of("VISIBLE", "HIDDEN").contains(visibility)) {
            throw invalid("visibility", "VISIBLE_OR_HIDDEN");
        }
        String why = reason == null ? "" : reason.strip();
        if (why.length() < 3 || why.length() > 1000) {
            throw invalid("reason", "LENGTH_3_1000");
        }
        if (visibility.equals(r.visibility())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "review is already " + visibility);
        }
        jdbc.update("UPDATE review SET visibility = ?, moderation_reason = ?, version = version + 1 WHERE id = ?", visibility,
                "HIDDEN".equals(visibility) ? why : null, reviewId);
        audit.record(new Entry(staffId, "review.moderation", Permission.CONTENT_MODERATE.code(), "review", reviewId.toString(), Outcome.ALLOWED, why,
                requestId, Map.of("visibility", visibility)));
        return review(reviewId);
    }

    /**
     * API-098: VISIBLE returns reported visible reviews (most reported first); HIDDEN returns hidden reviews so staff can
     * restore them. Newest first within equal report counts; bounded by limit.
     */
    public List<QueuedReview> queue(String visibility, int limit) {
        if (!Set.of("VISIBLE", "HIDDEN").contains(visibility)) {
            throw invalid("visibility", "VISIBLE_OR_HIDDEN");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        return jdbc.query("""
                SELECT r.id, r.order_id, r.kitchen_id, r.rating, r.text, r.visibility, r.moderation_reason, count(rr.id),
                       coalesce(array_agg(DISTINCT rr.reason_code ORDER BY rr.reason_code) FILTER (WHERE rr.id IS NOT NULL), '{}'),
                       r.created_at, r.version
                FROM review r LEFT JOIN review_report rr ON rr.review_id = r.id
                WHERE r.visibility = ?
                GROUP BY r.id
                HAVING r.visibility = 'HIDDEN' OR count(rr.id) > 0
                ORDER BY count(rr.id) DESC, r.created_at DESC, r.id DESC
                LIMIT ?""", (rs, n) -> new QueuedReview(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getInt(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getInt(8), List.of((String[]) rs.getArray(9).getArray()),
                rs.getTimestamp(10).toInstant(), rs.getLong(11)), visibility, limit);
    }

    public Review review(UUID id) {
        return jdbc.query("SELECT id, order_id, kitchen_id, rating, text, visibility, created_at, version FROM review WHERE id = ?",
                (rs, n) -> new Review(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class), rs.getInt(4), rs.getString(5),
                        rs.getString(6), rs.getTimestamp(7).toInstant(), rs.getLong(8)), id).getFirst();
    }

    @Override
    public Map<UUID, Rating> ratings(Collection<UUID> kitchenIds) {
        Map<UUID, Rating> out = new HashMap<>();
        if (kitchenIds.isEmpty()) {
            return out;
        }
        jdbc.query("""
                SELECT kitchen_id, count(*), round(avg(rating) * 10) FROM review WHERE visibility = 'VISIBLE' AND kitchen_id = ANY (?)
                GROUP BY kitchen_id""", rs -> {
                    out.put(rs.getObject(1, UUID.class), new Rating(rs.getInt(2), rs.getInt(3)));
                }, (Object) kitchenIds.toArray(UUID[]::new));
        return out;
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
