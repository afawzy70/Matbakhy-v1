package com.matbakhy.catalog.internal.discovery;

import com.matbakhy.catalog.api.ObjectStorage;
import com.matbakhy.catalog.internal.discovery.MealOffers.Offer;
import com.matbakhy.catalog.internal.discovery.MealOffers.SlotOffer;
import com.matbakhy.catalog.internal.meal.MealService.Category;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Public discovery (BR-016, BR-017): every filter, search and sort runs as one server query over all eligible
 * data — PUBLISHED meals of ACTIVE kitchens — with stable keyset pagination. A cursor is bound to its sort and
 * filters; reusing it after a filter change is rejected so clients restart from the first page.
 */
@Service
public class DiscoveryService {

    public enum Sort { NEWEST, PRICE_ASC, PRICE_DESC }

    public record Query(String q, UUID zoneId, LocalDate date, UUID slotId, Category category, Long minPriceMinor, Long maxPriceMinor,
                        UUID kitchenId, Sort sort, String cursor, int limit, boolean english) {
    }

    public record RatingSummary(int count, Integer averageTenths) {
    }

    public record KitchenSummary(UUID id, String slug, String name, RatingSummary ratingSummary) {
    }

    public record Photo(String url, String alt) {
    }

    public record Money(long amountMinor, String currency) {
    }

    public record MealSummary(UUID id, KitchenSummary kitchen, String title, Photo photo, String portionText, Money price,
                              List<SlotOffer> nextSlots, String availability) {
    }

    public record MealPage(List<MealSummary> items, String nextCursor, boolean hasMore) {
    }

    private record Row(UUID id, UUID kitchenId, String slug, String kitchenName, String titleAr, String titleEn, String portionAr,
                       String portionEn, long price, Instant publishedAt, String photoKey, String altAr, String altEn,
                       boolean pickup, boolean delivers) {
    }

    private final JdbcTemplate jdbc;
    private final MealOffers offers;
    private final ObjectStorage storage;
    private final Clock clock;

    DiscoveryService(JdbcTemplate jdbc, MealOffers offers, ObjectStorage storage, Clock clock) {
        this.jdbc = jdbc;
        this.offers = offers;
        this.storage = storage;
        this.clock = clock;
    }

    public MealPage search(Query in) {
        if (in.limit() < 1 || in.limit() > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        String q = in.q() == null || in.q().isBlank() ? null : in.q().strip();
        if (q != null && q.length() > 80) {
            throw invalid("q", "MAX_80");
        }
        if ((in.minPriceMinor() != null && in.minPriceMinor() < 0) || (in.maxPriceMinor() != null && in.maxPriceMinor() < 0)) {
            throw invalid("minPriceMinor", "NON_NEGATIVE");
        }
        if (in.minPriceMinor() != null && in.maxPriceMinor() != null && in.minPriceMinor() > in.maxPriceMinor()) {
            throw invalid("maxPriceMinor", "BELOW_MIN");
        }
        Sort sort = in.sort() == null ? Sort.NEWEST : in.sort();
        String filters = String.join("|", String.valueOf(q), String.valueOf(in.zoneId()), String.valueOf(in.date()), String.valueOf(in.slotId()),
                String.valueOf(in.category()), String.valueOf(in.minPriceMinor()), String.valueOf(in.maxPriceMinor()),
                String.valueOf(in.kitchenId()), sort.name());
        String fingerprint = sha256(filters).substring(0, 16);
        Instant now = clock.instant();

        StringBuilder sql = new StringBuilder("""
                SELECT m.id, m.kitchen_id, k.public_slug, k.display_name, m.title_ar, m.title_en, m.portion_ar, m.portion_en, m.price_minor,
                       m.published_at, k.pickup_enabled,
                       EXISTS (SELECT 1 FROM kitchen_zone kz WHERE kz.kitchen_id = k.id AND kz.delivery_enabled
                               AND (?::uuid IS NULL OR kz.zone_id = ?)) AS delivers,
                       p.public_key, p.alt_ar, p.alt_en
                FROM meal m
                JOIN kitchen k ON k.id = m.kitchen_id AND k.operating_state = 'ACTIVE'
                LEFT JOIN LATERAL (SELECT ma.public_key, mm.alt_ar, mm.alt_en FROM meal_media mm
                                   JOIN media_asset ma ON ma.id = mm.media_id AND ma.state = 'APPROVED' AND ma.public_key IS NOT NULL
                                   WHERE mm.meal_id = m.id ORDER BY mm.position LIMIT 1) p ON true
                WHERE m.status = 'PUBLISHED'
                """);
        List<Object> args = new ArrayList<>(java.util.Arrays.asList(in.zoneId(), in.zoneId()));
        if (q != null) {
            sql.append(" AND matbakhy_search_normalize(concat_ws(' ', m.title_ar, m.title_en, k.display_name)) LIKE ")
                    .append("'%' || replace(replace(replace(matbakhy_search_normalize(?), '\\', '\\\\'), '%', '\\%'), '_', '\\_') || '%'");
            args.add(q);
        }
        if (in.zoneId() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM kitchen_zone kz WHERE kz.kitchen_id = k.id AND kz.zone_id = ? AND kz.delivery_enabled)");
            args.add(in.zoneId());
        }
        if (in.date() != null || in.slotId() != null) {
            sql.append(" AND EXISTS (SELECT 1 FROM meal_slot ms JOIN cook_slot s ON s.id = ms.cook_slot_id WHERE ms.meal_id = m.id AND ")
                    .append(MealOffers.ORDERABLE).append(" AND (?::date IS NULL OR s.local_date = ?) AND (?::uuid IS NULL OR s.id = ?))");
            args.add(Timestamp.from(now));
            args.add(in.date());
            args.add(in.date());
            args.add(in.slotId());
            args.add(in.slotId());
        }
        if (in.category() != null) {
            sql.append(" AND m.category = ?");
            args.add(in.category().name());
        }
        if (in.minPriceMinor() != null) {
            sql.append(" AND m.price_minor >= ?");
            args.add(in.minPriceMinor());
        }
        if (in.maxPriceMinor() != null) {
            sql.append(" AND m.price_minor <= ?");
            args.add(in.maxPriceMinor());
        }
        if (in.kitchenId() != null) {
            sql.append(" AND m.kitchen_id = ?");
            args.add(in.kitchenId());
        }
        if (in.cursor() != null) {
            String[] c = decode(in.cursor(), sort, fingerprint);
            UUID afterId = UUID.fromString(c[1]);
            switch (sort) {
                case NEWEST -> {
                    sql.append(" AND (m.published_at, m.id) < (?, ?)");
                    args.add(Timestamp.from(Instant.parse(c[0])));
                }
                case PRICE_ASC -> {
                    sql.append(" AND (m.price_minor, m.id) > (?, ?)");
                    args.add(Long.parseLong(c[0]));
                }
                case PRICE_DESC -> {
                    sql.append(" AND (m.price_minor, m.id) < (?, ?)");
                    args.add(Long.parseLong(c[0]));
                }
            }
            args.add(afterId);
        }
        sql.append(switch (sort) {
            case NEWEST -> " ORDER BY m.published_at DESC, m.id DESC";
            case PRICE_ASC -> " ORDER BY m.price_minor, m.id";
            case PRICE_DESC -> " ORDER BY m.price_minor DESC, m.id DESC";
        });
        sql.append(" LIMIT ?");
        args.add(in.limit() + 1);

        List<Row> rows = jdbc.query(sql.toString(), (rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getObject("kitchen_id", UUID.class),
                rs.getString("public_slug"), rs.getString("display_name"), rs.getString("title_ar"), rs.getString("title_en"),
                rs.getString("portion_ar"), rs.getString("portion_en"), rs.getLong("price_minor"), rs.getTimestamp("published_at").toInstant(),
                rs.getString("public_key"), rs.getString("alt_ar"), rs.getString("alt_en"), rs.getBoolean("pickup_enabled"),
                rs.getBoolean("delivers")), args.toArray());
        boolean more = rows.size() > in.limit();
        List<Row> page = more ? rows.subList(0, in.limit()) : rows;

        Map<UUID, List<String>> modes = new LinkedHashMap<>();
        for (Row r : page) {
            List<String> m = new ArrayList<>();
            if (r.pickup()) {
                m.add("PICKUP");
            }
            if (r.delivers()) {
                m.add("DELIVERY");
            }
            modes.put(r.id(), m);
        }
        Map<UUID, Offer> byMeal = offers.offers(modes.keySet(), modes, in.date(), in.slotId());
        boolean en = in.english();
        List<MealSummary> items = page.stream().map(r -> {
            Offer offer = byMeal.get(r.id());
            Photo photo = r.photoKey() == null ? null : new Photo(storage.publicUrl(r.photoKey()).toString(),
                    en && r.altEn() != null ? r.altEn() : r.altAr() != null ? r.altAr() : "");
            return new MealSummary(r.id(), new KitchenSummary(r.kitchenId(), r.slug(), r.kitchenName(), new RatingSummary(0, null)),
                    en && r.titleEn() != null ? r.titleEn() : r.titleAr(), photo, en && r.portionEn() != null ? r.portionEn() : r.portionAr(),
                    new Money(r.price(), "EGP"), offer.nextSlots(), offer.availability());
        }).toList();
        String next = null;
        if (more) {
            Row last = page.getLast();
            String key = sort == Sort.NEWEST ? last.publishedAt().toString() : Long.toString(last.price());
            next = Base64.getUrlEncoder().withoutPadding().encodeToString(
                    String.join("|", sort.name(), fingerprint, key, last.id().toString()).getBytes(StandardCharsets.UTF_8));
        }
        return new MealPage(items, next, more);
    }

    private static String[] decode(String cursor, Sort sort, String fingerprint) {
        String[] parts;
        try {
            parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 4);
        } catch (RuntimeException e) {
            throw invalid("cursor", "INVALID");
        }
        if (parts.length != 4) {
            throw invalid("cursor", "INVALID");
        }
        if (!parts[0].equals(sort.name()) || !parts[1].equals(fingerprint)) {
            throw invalid("cursor", "CURSOR_FILTER_MISMATCH");
        }
        try {
            UUID.fromString(parts[3]);
            if (sort == Sort.NEWEST) {
                Instant.parse(parts[2]);
            } else {
                Long.parseLong(parts[2]);
            }
        } catch (RuntimeException e) {
            throw invalid("cursor", "INVALID");
        }
        return new String[] {parts[2], parts[3]};
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }
}
