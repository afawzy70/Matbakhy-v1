package com.matbakhy.catalog.internal.meal;

import com.matbakhy.catalog.api.KitchenRatings;
import com.matbakhy.catalog.api.MediaLibrary;
import com.matbakhy.catalog.api.MediaLibrary.Purpose;
import com.matbakhy.catalog.api.SellerDirectory;
import com.matbakhy.catalog.api.SellerDirectory.Seller;
import com.matbakhy.catalog.internal.discovery.MealOffers;
import com.matbakhy.catalog.internal.media.MediaService;
import com.matbakhy.identity.api.Permission;
import com.matbakhy.operations.api.AuditLog;
import com.matbakhy.operations.api.AuditLog.Entry;
import com.matbakhy.operations.api.AuditLog.Outcome;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ApiException.FieldError;
import com.matbakhy.shared.error.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.sql.Array;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Meals (BR-011, BR-012). Every content change creates an immutable revision snapshot that quotes and orders
 * reference; publication requires a complete declaration, approved photos and a kitchen that can sell. Meals
 * are archived, never deleted. Cook routes are scoped to the caller's own kitchen; others' meals are 404.
 */
@Service
public class MealService {

    public enum Allergen { GLUTEN, CRUSTACEANS, EGGS, FISH, PEANUTS, SOY, MILK, TREE_NUTS, CELERY, MUSTARD, SESAME, SULPHITES, LUPIN, MOLLUSCS }

    public enum Declaration { CONTAINS, NONE_DECLARED, UNKNOWN }

    public enum Action { PUBLISH, PAUSE, ARCHIVE }

    /** Proposed fixed category list (owner may revise); labels are localized by clients. */
    public enum Category { MAIN_DISHES, SIDES_SALADS, SOUPS, BAKED_GOODS, DESSERTS, BREAKFAST, READY_TO_COOK }

    static final int MAX_PHOTOS = 6;

    public record Photo(UUID mediaId, String altAr, String altEn) {
    }

    /** Partial update: null leaves a field unchanged, an empty string clears an optional text, a list replaces. */
    public record MealInput(String titleAr, String titleEn, String descriptionAr, String descriptionEn, String portionAr, String portionEn,
                            String ingredientsAr, String ingredientsEn, Declaration allergenDeclaration, List<Allergen> allergenCodes,
                            String allergenNote, Long priceMinor, List<Photo> photos, Category category) {
    }

    /** The versioned content; serialized as the revision snapshot. */
    public record MealContent(String titleAr, String titleEn, String descriptionAr, String descriptionEn, String portionAr, String portionEn,
                              String ingredientsAr, String ingredientsEn, Declaration allergenDeclaration, List<Allergen> allergenCodes,
                              String allergenNote, Long priceMinor, String currency, List<Photo> photos, Category category) {
    }

    /** moderationState HIDDEN means operations hid the meal; it is not shown or sold whatever its status. */
    public record CookMeal(UUID id, UUID kitchenId, String status, MealContent content, int revision, Instant publishedAt,
                           Instant archivedAt, Instant updatedAt, long version, String moderationState, String moderationReason) {
    }

    /** API-099 (D14): the active price and the price changes of an own meal, newest first. */
    public record PriceHistory(UUID mealId, Money activePrice, List<PriceChange> changes) {
    }

    public record PriceChange(Money price, Instant changedAt, int revision) {
    }

    /** API-071 row: what moderators need, no private kitchen data. */
    public record OpsMeal(UUID id, UUID kitchenId, String kitchenName, String status, String moderationState, String moderationReason,
                          String titleAr, String titleEn, Long priceMinor, Instant updatedAt, long version) {
    }

    public record OpsMealPage(List<OpsMeal> items, String nextCursor, boolean hasMore) {
    }

    public enum Moderation { HIDE, RESTORE }

    public record CookMealPage(List<CookMeal> items, String nextCursor, boolean hasMore) {
    }

    public record PublicPhoto(String url, String alt) {
    }

    public record RatingSummary(int count, Integer averageTenths) {
    }

    public record KitchenSummary(UUID id, String slug, String name, RatingSummary ratingSummary) {
    }

    public record Money(long amountMinor, String currency) {
    }

    /**
     * API-004 MealDetail (docs/engineering/03-api-contract.md core DTO), localized to one language, with up to five
     * next orderable slots and availability (MB-016). Reviews arrive with MB-024; until then rating counts are 0.
     * Never contains owner identity, private location or contact.
     */
    public record MealDetail(UUID id, KitchenSummary kitchen, String title, PublicPhoto photo, String portionText, Money price,
                             List<MealOffers.SlotOffer> nextSlots, String availability, String description, String ingredients,
                             List<String> allergenCodes, String allergenStatement, List<PublicPhoto> photos, RatingSummary reviewSummary,
                             int publishedRevision) {
    }

    private static final String COLUMNS = "id, kitchen_id, status, title_ar, title_en, description_ar, description_en, portion_ar, portion_en, "
            + "ingredients_ar, ingredients_en, allergen_declaration, allergen_codes, allergen_note, price_minor, currency, category, revision, "
            + "published_at, archived_at, created_at, updated_at, version, moderation_state, moderation_reason";

    private final JdbcTemplate jdbc;
    private final SellerDirectory sellers;
    private final MediaLibrary mediaLibrary;
    private final MediaService media;
    private final JsonMapper json;
    private final MealOffers offers;
    private final AuditLog audit;
    private final KitchenRatings ratings;
    private final Clock clock;

    MealService(JdbcTemplate jdbc, SellerDirectory sellers, MediaLibrary mediaLibrary, MediaService media, JsonMapper json, MealOffers offers,
                AuditLog audit, KitchenRatings ratings, Clock clock) {
        this.jdbc = jdbc;
        this.sellers = sellers;
        this.mediaLibrary = mediaLibrary;
        this.media = media;
        this.json = json;
        this.offers = offers;
        this.audit = audit;
        this.ratings = ratings;
        this.clock = clock;
    }

    // ---- cook side ----

    @Transactional
    public CookMeal create(UUID actorId, MealInput in) {
        Seller seller = requireSeller(actorId);
        MealContent content = merge(empty(), in);
        validate(actorId, content, false);
        UUID id = UUID.randomUUID();
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("INSERT INTO meal (id, kitchen_id, created_at, updated_at) VALUES (?, ?, ?, ?)", id, seller.kitchenId(), now, now);
        writeContent(id, content, 1);
        return cookMeal(id);
    }

    @Transactional
    public CookMeal update(UUID actorId, UUID mealId, Long expectedVersion, MealInput in) {
        Seller seller = requireSeller(actorId);
        Row row = lockOwn(seller, mealId);
        requireVersion(row, expectedVersion);
        if (row.status().equals("ARCHIVED")) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "archived meals are read-only");
        }
        MealContent content = merge(row.content(), in);
        boolean visible = row.status().equals("PUBLISHED") || row.status().equals("PAUSED");
        validate(actorId, content, visible);
        if (content.equals(row.content())) {
            return cookMeal(mealId);
        }
        writeContent(mealId, content, row.revision() + 1);
        return cookMeal(mealId);
    }

    /**
     * D14 price history, derived from the immutable revisions: an entry for the first priced revision and for every later
     * revision whose price differs from the previous entry; other edits add nothing. The active price is the meal's current
     * price (the authoritative record). Owner-scoped: another kitchen's meal is not found.
     */
    @Transactional(readOnly = true)
    public PriceHistory priceHistory(UUID actorId, UUID mealId) {
        Seller seller = requireSeller(actorId);
        Long active = jdbc.query("SELECT price_minor FROM meal WHERE id = ? AND kitchen_id = ?", rs -> rs.next() ? (Long) rs.getObject(1) : null,
                mealId, seller.kitchenId());
        if (active == null && jdbc.queryForObject("SELECT count(*) FROM meal WHERE id = ? AND kitchen_id = ?", Integer.class, mealId,
                seller.kitchenId()) == 0) {
            throw new ApiException(ErrorCode.NOT_FOUND, "meal not found");
        }
        List<PriceChange> changes = new ArrayList<>();
        Long previous = null;
        for (Map<String, Object> r : jdbc.queryForList(
                "SELECT revision, price_minor, created_at FROM meal_revision WHERE meal_id = ? AND price_minor IS NOT NULL ORDER BY revision", mealId)) {
            long price = ((Number) r.get("price_minor")).longValue();
            if (previous == null || previous != price) {
                changes.add(new PriceChange(new Money(price, "EGP"), ((java.sql.Timestamp) r.get("created_at")).toInstant(), (Integer) r.get("revision")));
                previous = price;
            }
        }
        java.util.Collections.reverse(changes);
        return new PriceHistory(mealId, active == null ? null : new Money(active, "EGP"), changes);
    }

    @Transactional
    public CookMeal changePublication(UUID actorId, UUID mealId, Long expectedVersion, Action action) {
        Seller seller = requireSeller(actorId);
        Row row = lockOwn(seller, mealId);
        requireVersion(row, expectedVersion);
        Instant now = clock.instant();
        switch (action) {
            case PUBLISH -> {
                if (!row.status().equals("DRAFT") && !row.status().equals("PAUSED")) {
                    throw new ApiException(ErrorCode.STATE_CONFLICT, "cannot publish from " + row.status());
                }
                if (!seller.canSell()) {
                    throw new ApiException(ErrorCode.KITCHEN_UNAVAILABLE, "kitchen cannot sell");
                }
                validate(actorId, row.content(), true);
                jdbc.update("""
                        UPDATE meal SET status = 'PUBLISHED', published_at = coalesce(published_at, ?), updated_at = ?, version = version + 1
                        WHERE id = ?""", Timestamp.from(now), Timestamp.from(now), mealId);
            }
            case PAUSE -> {
                if (!row.status().equals("PUBLISHED")) {
                    throw new ApiException(ErrorCode.STATE_CONFLICT, "cannot pause from " + row.status());
                }
                jdbc.update("UPDATE meal SET status = 'PAUSED', updated_at = ?, version = version + 1 WHERE id = ?", Timestamp.from(now), mealId);
            }
            case ARCHIVE -> {
                if (row.status().equals("ARCHIVED")) {
                    throw new ApiException(ErrorCode.STATE_CONFLICT, "already archived");
                }
                jdbc.update("UPDATE meal SET status = 'ARCHIVED', archived_at = ?, updated_at = ?, version = version + 1 WHERE id = ?",
                        Timestamp.from(now), Timestamp.from(now), mealId);
            }
        }
        return cookMeal(mealId);
    }

    public CookMealPage list(UUID actorId, String status, String q, String cursor, int limit) {
        Seller seller = requireSeller(actorId);
        if (status != null && !Set.of("DRAFT", "PUBLISHED", "PAUSED", "ARCHIVED").contains(status)) {
            throw invalid("status", "UNKNOWN_STATUS");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        String search = q == null || q.isBlank() ? null : q.strip();
        if (search != null && search.length() > 80) {
            throw invalid("q", "MAX_80");
        }
        String like = search == null ? null : "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        Instant afterTime = Instant.EPOCH;
        UUID afterId = new UUID(0, 0);
        if (cursor != null) {
            try {
                String[] parts = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8).split("\\|", 2);
                afterTime = Instant.parse(parts[0]);
                afterId = UUID.fromString(parts[1]);
            } catch (RuntimeException e) {
                throw invalid("cursor", "INVALID");
            }
        }
        List<Row> rows = jdbc.query("SELECT " + COLUMNS + " FROM meal WHERE kitchen_id = ? AND (?::text IS NULL OR status = ?) "
                + "AND (?::text IS NULL OR title_ar ILIKE ? OR title_en ILIKE ?) AND (created_at, id) > (?, ?) ORDER BY created_at, id LIMIT ?",
                this::map, seller.kitchenId(), status, status, like, like, like, Timestamp.from(afterTime), afterId, limit + 1);
        boolean more = rows.size() > limit;
        List<Row> page = more ? rows.subList(0, limit) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                (page.getLast().createdAt() + "|" + page.getLast().id()).getBytes(StandardCharsets.UTF_8)) : null;
        return new CookMealPage(page.stream().map(this::toCookMeal).toList(), next, more);
    }

    // ---- public side ----

    /** API-004: only a published meal of a kitchen that can sell; everything else is indistinguishable from missing. */
    public MealDetail publicMeal(UUID mealId, boolean english) {
        Row row = jdbc.query("SELECT " + COLUMNS + " FROM meal WHERE id = ? AND status = 'PUBLISHED' AND moderation_state = 'VISIBLE'", this::map, mealId).stream()
                .findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "meal not public"));
        Seller seller = sellers.byKitchen(row.kitchenId()).filter(Seller::canSell)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "kitchen not selling"));
        MealContent c = row.content();
        boolean en = english && c.titleEn() != null;
        List<PublicPhoto> photos = new ArrayList<>();
        for (Photo p : c.photos()) {
            String alt = en && p.altEn() != null ? p.altEn() : p.altAr() != null ? p.altAr() : "";
            media.publicUrl(p.mediaId()).ifPresent(url -> photos.add(new PublicPhoto(url.toString(), alt)));
        }
        List<String> allergenCodes = c.allergenDeclaration() == Declaration.CONTAINS
                ? c.allergenCodes().stream().map(Enum::name).toList() : List.of(c.allergenDeclaration().name());
        KitchenRatings.Rating r = ratings.ratings(List.of(seller.kitchenId())).getOrDefault(seller.kitchenId(), new KitchenRatings.Rating(0, null));
        RatingSummary noReviews = new RatingSummary(r.count(), r.averageTenths());
        List<String> modes = new ArrayList<>();
        if (seller.pickupEnabled()) {
            modes.add("PICKUP");
        }
        if (!seller.deliveryZoneIds().isEmpty()) {
            modes.add("DELIVERY");
        }
        MealOffers.Offer offer = offers.offers(List.of(row.id()), java.util.Map.of(row.id(), modes), null, null).get(row.id());
        return new MealDetail(row.id(), new KitchenSummary(seller.kitchenId(), seller.publicSlug(), seller.displayName(), noReviews),
                pick(en, c.titleEn(), c.titleAr()), photos.isEmpty() ? null : photos.getFirst(), pick(en, c.portionEn(), c.portionAr()),
                new Money(c.priceMinor(), c.currency()), offer.nextSlots(), offer.availability(), pick(en, c.descriptionEn(), c.descriptionAr()),
                pick(en, c.ingredientsEn(), c.ingredientsAr()), allergenCodes, c.allergenNote() == null ? "" : c.allergenNote(), photos,
                noReviews, row.revision());
    }

    private static String pick(boolean english, String en, String ar) {
        return english && en != null ? en : ar;
    }

    /** The immutable content of a revision, for quotes and order snapshots (MB-017/MB-019). */
    public MealContent revision(UUID mealId, int revision) {
        String snapshot = jdbc.query("SELECT snapshot::text FROM meal_revision WHERE meal_id = ? AND revision = ?",
                rs -> rs.next() ? rs.getString(1) : null, mealId, revision);
        if (snapshot == null) {
            throw new ApiException(ErrorCode.NOT_FOUND, "meal revision not found");
        }
        return json.readValue(snapshot, MealContent.class);
    }

    // ---- operations moderation (API-071/072) ----

    public OpsMealPage catalogue(String status, String moderation, UUID kitchenId, String q, String cursor, int limit) {
        if (status != null && !Set.of("DRAFT", "PUBLISHED", "PAUSED", "ARCHIVED").contains(status)) {
            throw invalid("status", "UNKNOWN_STATUS");
        }
        if (moderation != null && !Set.of("VISIBLE", "HIDDEN").contains(moderation)) {
            throw invalid("moderationState", "UNKNOWN_STATE");
        }
        if (limit < 1 || limit > 100) {
            throw invalid("limit", "RANGE_1_100");
        }
        String search = q == null || q.isBlank() ? null : q.strip();
        if (search != null && search.length() > 80) {
            throw invalid("q", "MAX_80");
        }
        String like = search == null ? null : "%" + search.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
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
        List<OpsMeal> rows = jdbc.query("""
                SELECT m.id, m.kitchen_id, k.display_name, m.status, m.moderation_state, m.moderation_reason, m.title_ar, m.title_en,
                       m.price_minor, m.updated_at, m.version
                FROM meal m JOIN kitchen k ON k.id = m.kitchen_id
                WHERE (?::text IS NULL OR m.status = ?) AND (?::text IS NULL OR m.moderation_state = ?) AND (?::uuid IS NULL OR m.kitchen_id = ?)
                  AND (?::text IS NULL OR m.title_ar ILIKE ? OR m.title_en ILIKE ? OR k.display_name ILIKE ?)
                  AND (m.updated_at, m.id) < (?, ?)
                ORDER BY m.updated_at DESC, m.id DESC LIMIT ?""",
                (rs, n) -> new OpsMeal(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3), rs.getString(4), rs.getString(5),
                        rs.getString(6), rs.getString(7), rs.getString(8), rs.getObject(9) == null ? null : rs.getLong(9),
                        rs.getTimestamp(10).toInstant(), rs.getLong(11)),
                status, status, moderation, moderation, kitchenId, kitchenId, like, like, like, like, Timestamp.from(beforeTime), beforeId, limit + 1);
        boolean more = rows.size() > limit;
        List<OpsMeal> page = more ? rows.subList(0, limit) : rows;
        String next = more ? Base64.getUrlEncoder().withoutPadding().encodeToString(
                (page.getLast().updatedAt() + "|" + page.getLast().id()).getBytes(StandardCharsets.UTF_8)) : null;
        return new OpsMealPage(page, next, more);
    }

    /** Hide or restore a meal (any status); reasoned and audited. The cook's content and status stay unchanged. */
    @Transactional
    public CookMeal moderate(UUID staffId, UUID mealId, Moderation action, String reason, Long expectedVersion, String requestId) {
        Row row = jdbc.query("SELECT " + COLUMNS + " FROM meal WHERE id = ? FOR UPDATE", this::map, mealId).stream().findFirst()
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "meal not found"));
        requireVersion(row, expectedVersion);
        String trimmed = reason == null ? null : reason.strip();
        if (trimmed == null || trimmed.length() < 3 || trimmed.length() > 1000) {
            throw invalid("reason", "LENGTH_3_1000");
        }
        String target = action == Moderation.HIDE ? "HIDDEN" : "VISIBLE";
        if (target.equals(row.moderationState())) {
            throw new ApiException(ErrorCode.STATE_CONFLICT, "meal is already " + target);
        }
        Instant now = clock.instant();
        jdbc.update("""
                UPDATE meal SET moderation_state = ?, moderation_reason = ?, moderated_at = ?, moderated_by = ?, version = version + 1 WHERE id = ?""",
                target, action == Moderation.HIDE ? trimmed : null, Timestamp.from(now), staffId, mealId);
        audit.record(new Entry(staffId, "meal.moderation", Permission.CONTENT_MODERATE.code(), "meal", mealId.toString(), Outcome.ALLOWED, trimmed,
                requestId, Map.of("action", action.name(), "kitchenId", row.kitchenId().toString())));
        return cookMeal(mealId);
    }

    // ---- internals ----

    private record Row(UUID id, UUID kitchenId, String status, MealContent content, int revision, Instant publishedAt, Instant archivedAt,
                       Instant createdAt, Instant updatedAt, long version, String moderationState, String moderationReason) {
    }

    private Seller requireSeller(UUID actorId) {
        return sellers.byOwner(actorId).orElseThrow(() -> new ApiException(ErrorCode.FORBIDDEN, "caller has no approved kitchen"));
    }

    private Row lockOwn(Seller seller, UUID mealId) {
        return jdbc.query("SELECT " + COLUMNS + " FROM meal WHERE id = ? AND kitchen_id = ? FOR UPDATE", this::map, mealId, seller.kitchenId())
                .stream().findFirst().orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "meal not found"));
    }

    private static void requireVersion(Row row, Long expectedVersion) {
        if (expectedVersion == null) {
            throw invalid("expectedVersion", "REQUIRED");
        }
        if (expectedVersion != row.version()) {
            throw new ApiException(ErrorCode.VERSION_CONFLICT, "stale meal version");
        }
    }

    private void writeContent(UUID mealId, MealContent c, int revision) {
        jdbc.update("""
                UPDATE meal SET title_ar = ?, title_en = ?, description_ar = ?, description_en = ?, portion_ar = ?, portion_en = ?,
                       ingredients_ar = ?, ingredients_en = ?, allergen_declaration = ?, allergen_codes = ?::text[], allergen_note = ?,
                       price_minor = ?, category = ?, revision = ?, updated_at = ?, version = version + ?
                WHERE id = ?""",
                c.titleAr(), c.titleEn(), c.descriptionAr(), c.descriptionEn(), c.portionAr(), c.portionEn(), c.ingredientsAr(),
                c.ingredientsEn(), c.allergenDeclaration() == null ? null : c.allergenDeclaration().name(),
                c.allergenCodes().stream().map(Enum::name).collect(Collectors.joining(",", "{", "}")), c.allergenNote(), c.priceMinor(),
                c.category() == null ? null : c.category().name(), revision, Timestamp.from(clock.instant()), revision == 1 ? 0 : 1, mealId);
        jdbc.update("DELETE FROM meal_media WHERE meal_id = ?", mealId);
        for (int i = 0; i < c.photos().size(); i++) {
            Photo p = c.photos().get(i);
            jdbc.update("INSERT INTO meal_media (meal_id, media_id, position, alt_ar, alt_en) VALUES (?, ?, ?, ?, ?)",
                    mealId, p.mediaId(), i, p.altAr(), p.altEn());
        }
        jdbc.update("INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor, created_at) VALUES (?, ?, ?::jsonb, ?, ?)",
                mealId, revision, json.writeValueAsString(c), c.priceMinor(), Timestamp.from(clock.instant()));
    }

    private static MealContent empty() {
        return new MealContent(null, null, null, null, null, null, null, null, null, List.of(), null, null, "EGP", List.of(), null);
    }

    private static MealContent merge(MealContent base, MealInput in) {
        Declaration declaration = in.allergenDeclaration() != null ? in.allergenDeclaration() : base.allergenDeclaration();
        List<Allergen> codes = in.allergenCodes() != null ? List.copyOf(new java.util.TreeSet<>(in.allergenCodes()))
                : in.allergenDeclaration() != null && in.allergenDeclaration() != Declaration.CONTAINS ? List.of() : base.allergenCodes();
        return new MealContent(text(in.titleAr(), base.titleAr()), text(in.titleEn(), base.titleEn()),
                text(in.descriptionAr(), base.descriptionAr()), text(in.descriptionEn(), base.descriptionEn()),
                text(in.portionAr(), base.portionAr()), text(in.portionEn(), base.portionEn()),
                text(in.ingredientsAr(), base.ingredientsAr()), text(in.ingredientsEn(), base.ingredientsEn()), declaration, codes,
                text(in.allergenNote(), base.allergenNote()), in.priceMinor() != null ? in.priceMinor() : base.priceMinor(), "EGP",
                in.photos() != null ? List.copyOf(in.photos()) : base.photos(), in.category() != null ? in.category() : base.category());
    }

    private static String text(String value, String current) {
        if (value == null) {
            return current;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** Field rules always; with {@code complete}, also the publication requirements (BR-011). */
    private void validate(UUID ownerId, MealContent c, boolean complete) {
        List<FieldError> errors = new ArrayList<>();
        length(errors, "titleAr", c.titleAr(), 2, 120);
        length(errors, "titleEn", c.titleEn(), 2, 120);
        length(errors, "descriptionAr", c.descriptionAr(), 10, 2000);
        length(errors, "descriptionEn", c.descriptionEn(), 10, 2000);
        length(errors, "portionAr", c.portionAr(), 2, 120);
        length(errors, "portionEn", c.portionEn(), 2, 120);
        length(errors, "ingredientsAr", c.ingredientsAr(), 3, 2000);
        length(errors, "ingredientsEn", c.ingredientsEn(), 3, 2000);
        length(errors, "allergenNote", c.allergenNote(), 1, 500);
        if (c.priceMinor() != null && (c.priceMinor() < 1 || c.priceMinor() > 1_000_000)) {
            errors.add(new FieldError("priceMinor", "RANGE_1_1000000"));
        }
        if (c.allergenDeclaration() == Declaration.CONTAINS && c.allergenCodes().isEmpty()) {
            errors.add(new FieldError("allergenCodes", "REQUIRED_FOR_CONTAINS"));
        }
        if (c.allergenDeclaration() != Declaration.CONTAINS && !c.allergenCodes().isEmpty()) {
            errors.add(new FieldError("allergenCodes", "ONLY_WITH_CONTAINS"));
        }
        if (c.photos().size() > MAX_PHOTOS) {
            errors.add(new FieldError("photos", "MAX_6"));
        }
        Set<UUID> seen = new HashSet<>();
        for (Photo p : c.photos()) {
            if (p.mediaId() == null || !seen.add(p.mediaId())) {
                errors.add(new FieldError("photos", "DUPLICATE_OR_MISSING"));
                break;
            }
            length(errors, "photos.alt", p.altAr(), 0, 200);
            length(errors, "photos.alt", p.altEn(), 0, 200);
            if (!mediaLibrary.approvedFor(ownerId, p.mediaId(), Purpose.MEAL_PHOTO)) {
                errors.add(new FieldError("photos", "PHOTO_NOT_APPROVED"));
                break;
            }
        }
        if (complete) {
            required(errors, "titleAr", c.titleAr());
            required(errors, "descriptionAr", c.descriptionAr());
            required(errors, "portionAr", c.portionAr());
            required(errors, "ingredientsAr", c.ingredientsAr());
            if (c.allergenDeclaration() == null) {
                errors.add(new FieldError("allergenDeclaration", "REQUIRED"));
            }
            if (c.priceMinor() == null) {
                errors.add(new FieldError("priceMinor", "REQUIRED"));
            }
            if (c.photos().isEmpty()) {
                errors.add(new FieldError("photos", "APPROVED_PHOTO_REQUIRED"));
            }
        }
        if (!errors.isEmpty()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, complete ? "meal not publishable" : "invalid meal", errors);
        }
    }

    private static void required(List<FieldError> errors, String field, String value) {
        if (value == null) {
            errors.add(new FieldError(field, "REQUIRED"));
        }
    }

    private static void length(List<FieldError> errors, String field, String value, int min, int max) {
        if (value != null && (value.length() < min || value.length() > max)) {
            errors.add(new FieldError(field, "LENGTH_" + min + "_" + max));
        }
    }

    private static ApiException invalid(String field, String code) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, "invalid " + field, List.of(new FieldError(field, code)));
    }

    private CookMeal cookMeal(UUID mealId) {
        return toCookMeal(jdbc.query("SELECT " + COLUMNS + " FROM meal WHERE id = ?", this::map, mealId).getFirst());
    }

    private CookMeal toCookMeal(Row r) {
        return new CookMeal(r.id(), r.kitchenId(), r.status(), r.content(), r.revision(), r.publishedAt(), r.archivedAt(), r.updatedAt(),
                r.version(), r.moderationState(), r.moderationReason());
    }

    private Row map(ResultSet rs, int n) throws SQLException {
        UUID id = rs.getObject("id", UUID.class);
        List<Photo> photos = jdbc.query("SELECT media_id, alt_ar, alt_en FROM meal_media WHERE meal_id = ? ORDER BY position",
                (p, i) -> new Photo(p.getObject(1, UUID.class), p.getString(2), p.getString(3)), id);
        String declaration = rs.getString("allergen_declaration");
        Object price = rs.getObject("price_minor");
        MealContent content = new MealContent(rs.getString("title_ar"), rs.getString("title_en"), rs.getString("description_ar"),
                rs.getString("description_en"), rs.getString("portion_ar"), rs.getString("portion_en"), rs.getString("ingredients_ar"),
                rs.getString("ingredients_en"), declaration == null ? null : Declaration.valueOf(declaration), allergens(rs.getArray("allergen_codes")),
                rs.getString("allergen_note"), price == null ? null : ((Number) price).longValue(), rs.getString("currency"), photos,
                rs.getString("category") == null ? null : Category.valueOf(rs.getString("category")));
        return new Row(id, rs.getObject("kitchen_id", UUID.class), rs.getString("status"), content, rs.getInt("revision"),
                instant(rs, "published_at"), instant(rs, "archived_at"), instant(rs, "created_at"), instant(rs, "updated_at"),
                rs.getLong("version"), rs.getString("moderation_state"), rs.getString("moderation_reason"));
    }

    private static List<Allergen> allergens(Array array) throws SQLException {
        if (array == null) {
            return List.of();
        }
        return Arrays.stream((Object[]) array.getArray()).map(Objects::toString).map(Allergen::valueOf).sorted().toList();
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp t = rs.getTimestamp(column);
        return t == null ? null : t.toInstant();
    }
}
