package com.matbakhy.catalog.internal.activity;

import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.sql.Date;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * API-110 (MB-054, D17): records organic activity once per visitor, subject, kind and Cairo day. The visitor id is a
 * random value the browser keeps for its session; only an HMAC under a per-day key is stored, so it cannot be reversed
 * or followed across days. Declared crawlers, unknown subjects and events past the daily cap are dropped silently: the
 * answer never tells a caller whether an event counted.
 */
@Service
@EnableConfigurationProperties(ActivityProperties.class)
public class ActivityService {

    static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");
    static final Set<String> KINDS = Set.of("MEAL_IMPRESSION", "MEAL_VIEW", "KITCHEN_VIEW", "ADD_TO_CART");
    static final int MAX_EVENTS_PER_REQUEST = 50;
    private static final Pattern VISITOR = Pattern.compile("[A-Za-z0-9-]{8,64}");
    private static final Pattern CRAWLER = Pattern.compile("(?i)(bot|crawl|spider|slurp|facebookexternalhit)");

    public record Event(String kind, UUID subjectId) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;
    private final byte[] secret;
    private final int cap;

    ActivityService(JdbcTemplate jdbc, Clock clock, ActivityProperties props) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.secret = props.hashKey().getBytes(StandardCharsets.UTF_8);
        this.cap = props.maxEventsPerVisitorDay();
    }

    @Transactional
    public void record(String visitorId, List<Event> events, String userAgent) {
        if (visitorId == null || !VISITOR.matcher(visitorId).matches()) {
            throw invalid("visitorId", "PATTERN_8_64");
        }
        if (events == null || events.isEmpty() || events.size() > MAX_EVENTS_PER_REQUEST) {
            throw invalid("events", "SIZE_1_50");
        }
        for (Event e : events) {
            if (e == null || e.kind() == null || !KINDS.contains(e.kind()) || e.subjectId() == null) {
                throw invalid("events", "KIND_AND_SUBJECT_REQUIRED");
            }
        }
        if (userAgent != null && CRAWLER.matcher(userAgent).find()) {
            return;
        }
        LocalDate day = LocalDate.ofInstant(clock.instant(), CAIRO);
        byte[] visitor = visitorHash(day, visitorId);
        Date sqlDay = Date.valueOf(day);
        int used = jdbc.queryForObject("SELECT count(*) FROM activity_daily WHERE day = ? AND visitor_hash = ?", Integer.class, sqlDay, visitor);
        for (Event e : events) {
            if (used >= cap) {
                return;
            }
            UUID kitchen = kitchenOf(e);
            if (kitchen == null) {
                continue;
            }
            used += jdbc.update("""
                    INSERT INTO activity_daily (day, kind, subject_id, kitchen_id, visitor_hash) VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT DO NOTHING""", sqlDay, e.kind(), e.subjectId(), kitchen, visitor);
        }
    }

    private UUID kitchenOf(Event e) {
        List<UUID> found = "KITCHEN_VIEW".equals(e.kind())
                ? jdbc.queryForList("SELECT id FROM kitchen WHERE id = ?", UUID.class, e.subjectId())
                : jdbc.queryForList("SELECT kitchen_id FROM meal WHERE id = ? AND status <> 'ARCHIVED'", UUID.class, e.subjectId());
        return found.isEmpty() ? null : found.getFirst();
    }

    /** HMAC-SHA256 of the visitor id under a key derived from the server secret and the day (32 bytes). */
    byte[] visitorHash(LocalDate day, String visitorId) {
        byte[] dayKey = hmac(secret, ("activity-day|" + day).getBytes(StandardCharsets.UTF_8));
        return hmac(dayKey, visitorId.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("activity hash failed", ex);
        }
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
