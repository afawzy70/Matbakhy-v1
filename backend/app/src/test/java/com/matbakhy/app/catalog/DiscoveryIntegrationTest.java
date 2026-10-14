package com.matbakhy.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestKitchens;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TC-016, TC-017, TC-116 on PostgreSQL: server-side search/filter/sort over all eligible data, stable keyset
 * pagination without duplicates, exclusion of unpublished and non-active supply, zone/date/slot serviceability,
 * availability states, Arabic-normalized search and the public zone list. Meals and slots are inserted as
 * synthetic fixtures (their own workflows are covered by MealIntegrationTest and SlotIntegrationTest).
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class DiscoveryIntegrationTest {

    static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;

    UUID reviewer;
    UUID kitchen;
    String token;

    @BeforeEach
    void setUp() {
        reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        kitchen = newKitchen();
        token = "tok" + UUID.randomUUID().toString().substring(0, 8);
    }

    private UUID newKitchen() {
        return TestKitchens.approvedKitchen(jdbc, logins.login(UUID.randomUUID().toString(), "Cook").getPrincipal().id(), reviewer);
    }

    private UUID meal(UUID kitchenId, String status, String titleAr, String titleEn, long price, String category, Instant publishedAt) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO meal (id, kitchen_id, status, title_ar, title_en, description_ar, portion_ar, ingredients_ar, allergen_declaration,
                                  price_minor, category, published_at, archived_at)
                VALUES (?, ?, ?, ?, ?, 'وصف تجريبي للوجبة', 'علبة', 'مكونات تجريبية', 'NONE_DECLARED', ?, ?, ?, ?)""",
                id, kitchenId, status, titleAr, titleEn, price, category,
                status.equals("DRAFT") ? null : Timestamp.from(publishedAt), status.equals("ARCHIVED") ? Timestamp.from(publishedAt) : null);
        return id;
    }

    private UUID published(UUID kitchenId, String titleAr, long price) {
        return meal(kitchenId, "PUBLISHED", titleAr, null, price, null, Instant.now());
    }

    /** OPEN slot at a Cairo local date/hour with the given meal capacities and already-used portions. */
    private UUID slot(UUID kitchenId, LocalDate date, int hour, int maxPortions, int slotUsed, Object... mealCapacityUsed) {
        ZonedDateTime start = date.atTime(hour, 0).atZone(CAIRO);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO cook_slot (id, kitchen_id, local_date, starts_at, ends_at, lead_minutes, cutoff_at, max_portions, reserved, state)
                VALUES (?, ?, ?, ?, ?, 120, ?, ?, ?, 'OPEN')""",
                id, kitchenId, date, Timestamp.from(start.toInstant()), Timestamp.from(start.plusHours(1).toInstant()),
                Timestamp.from(start.minusMinutes(120).toInstant()), maxPortions, slotUsed);
        for (int i = 0; i < mealCapacityUsed.length; i += 3) {
            jdbc.update("INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity, reserved) VALUES (?, ?, ?, ?, ?)", UUID.randomUUID(), id,
                    mealCapacityUsed[i], mealCapacityUsed[i + 1], mealCapacityUsed[i + 2]);
        }
        return id;
    }

    private LocalDate daysAhead(int days) {
        return LocalDate.now(CAIRO).plusDays(days);
    }

    private JsonNode ok(MockHttpServletRequestBuilder r) throws Exception {
        MockHttpServletResponse res = mvc.perform(r).andReturn().getResponse();
        assertThat(res.getStatus()).as(res.getContentAsString()).isEqualTo(200);
        return json.readTree(res.getContentAsString());
    }

    private List<String> allIds(MockHttpServletRequestBuilder base, String... params) throws Exception {
        List<String> ids = new ArrayList<>();
        String cursor = null;
        int pages = 0;
        do {
            MockHttpServletRequestBuilder r = get("/api/v1/meals");
            for (int i = 0; i < params.length; i += 2) {
                r = r.param(params[i], params[i + 1]);
            }
            if (cursor != null) {
                r = r.param("cursor", cursor);
            }
            JsonNode page = ok(r);
            page.get("items").forEach(m -> ids.add(m.get("id").asString()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
            assertThat(page.get("hasMore").asBoolean()).isEqualTo(cursor != null);
            pages++;
        } while (cursor != null && pages < 50);
        return ids;
    }

    // ---- TC-016: full server query, stable pagination ----

    @Test
    void a_matching_meal_beyond_the_first_page_is_found_by_a_fresh_filtered_query() throws Exception {
        UUID oldest = meal(kitchen, "PUBLISHED", "فطير مشلتت " + token, null, 5000, "BAKED_GOODS", Instant.now().minus(Duration.ofDays(30)));
        for (int i = 0; i < 25; i++) {
            meal(kitchen, "PUBLISHED", "وجبة رقم " + i, null, 7000, "MAIN_DISHES", Instant.now().minus(Duration.ofMinutes(i)));
        }
        JsonNode first = ok(get("/api/v1/meals").param("kitchenId", kitchen.toString()).param("limit", "20"));
        assertThat(first.get("items").size()).isEqualTo(20);
        assertThat(first.get("hasMore").asBoolean()).isTrue();
        assertThat(first.toString()).doesNotContain(oldest.toString());

        JsonNode searched = ok(get("/api/v1/meals").param("kitchenId", kitchen.toString()).param("q", token));
        assertThat(searched.get("items").size()).isEqualTo(1);
        assertThat(searched.get("items").get(0).get("id").asString()).isEqualTo(oldest.toString());
        JsonNode byCategory = ok(get("/api/v1/meals").param("kitchenId", kitchen.toString()).param("categoryId", "BAKED_GOODS"));
        assertThat(byCategory.get("items").get(0).get("id").asString()).isEqualTo(oldest.toString());
        JsonNode byPrice = ok(get("/api/v1/meals").param("kitchenId", kitchen.toString()).param("maxPriceMinor", "6000"));
        assertThat(byPrice.get("items").size()).isEqualTo(1);

        MockHttpServletResponse reused = mvc.perform(get("/api/v1/meals").param("kitchenId", kitchen.toString()).param("q", token)
                .param("cursor", first.get("nextCursor").asString())).andReturn().getResponse();
        assertThat(reused.getStatus()).as("a cursor from other filters is refused").isEqualTo(422);
        assertThat(reused.getContentAsString()).contains("CURSOR_FILTER_MISMATCH");
    }

    @Test
    void every_sort_pages_through_all_eligible_meals_exactly_once() throws Exception {
        List<String> expected = new ArrayList<>();
        Instant base = Instant.now().minus(Duration.ofHours(1));
        for (int i = 0; i < 23; i++) {
            // Repeated prices and identical publication times exercise the id tie-breaker.
            expected.add(meal(kitchen, "PUBLISHED", "صنف " + i, null, 1000L * (i % 4 + 1), null, base.plusSeconds(i % 3)).toString());
        }
        for (String sort : List.of("NEWEST", "PRICE_ASC", "PRICE_DESC")) {
            List<String> ids = allIds(get("/api/v1/meals"), "kitchenId", kitchen.toString(), "sort", sort, "limit", "4");
            assertThat(ids).as(sort).hasSize(23).doesNotHaveDuplicates().containsExactlyInAnyOrderElementsOf(expected);
        }
        JsonNode cheap = ok(get("/api/v1/meals").param("kitchenId", kitchen.toString()).param("sort", "PRICE_ASC").param("limit", "100"));
        long previous = 0;
        for (JsonNode m : cheap.get("items")) {
            assertThat(m.get("price").get("amountMinor").asLong()).isGreaterThanOrEqualTo(previous);
            previous = m.get("price").get("amountMinor").asLong();
        }
        assertThat(mvc.perform(get("/api/v1/meals").param("sort", "POPULAR")).andReturn().getResponse().getStatus()).isEqualTo(400);
        assertThat(mvc.perform(get("/api/v1/meals").param("minPriceMinor", "500").param("maxPriceMinor", "100")).andReturn().getResponse()
                .getStatus()).isEqualTo(422);
        assertThat(mvc.perform(get("/api/v1/meals").param("cursor", "%%%")).andReturn().getResponse().getStatus()).isEqualTo(422);
    }

    // ---- TC-017 / BR-017: only purchasable supply ----

    @Test
    void unpublished_meals_and_meals_of_paused_or_suspended_kitchens_are_excluded() throws Exception {
        UUID live = published(kitchen, "متاح " + token, 5000);
        meal(kitchen, "DRAFT", "مسودة " + token, null, 5000, null, Instant.now());
        meal(kitchen, "PAUSED", "موقوف " + token, null, 5000, null, Instant.now());
        meal(kitchen, "ARCHIVED", "مؤرشف " + token, null, 5000, null, Instant.now());
        UUID pausedKitchen = newKitchen();
        UUID pausedMeal = published(pausedKitchen, "مطبخ متوقف " + token, 5000);
        jdbc.update("UPDATE kitchen SET operating_state = 'PAUSED' WHERE id = ?", pausedKitchen);
        UUID suspendedKitchen = newKitchen();
        published(suspendedKitchen, "مطبخ معلق " + token, 5000);
        jdbc.update("UPDATE kitchen SET operating_state = 'SUSPENDED' WHERE id = ?", suspendedKitchen);

        assertThat(allIds(get("/api/v1/meals"), "q", token)).containsExactly(live.toString());
        assertThat(mvc.perform(get("/api/v1/meals/" + pausedMeal)).andReturn().getResponse().getStatus()).isEqualTo(404);
        String slug = jdbc.queryForObject("SELECT public_slug FROM kitchen WHERE id = ?", String.class, pausedKitchen);
        assertThat(ok(get("/api/v1/kitchens/" + slug)).get("available").asBoolean()).as("paused cook shown as unavailable").isFalse();

        jdbc.update("UPDATE kitchen SET operating_state = 'ACTIVE' WHERE id = ?", pausedKitchen);
        assertThat(allIds(get("/api/v1/meals"), "q", token)).containsExactlyInAnyOrder(live.toString(), pausedMeal.toString());
    }

    // ---- serviceability: zone, date, slot and availability ----

    @Test
    void zone_date_and_slot_filters_return_only_serviceable_meals_with_their_availability() throws Exception {
        UUID zone = UUID.randomUUID();
        jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                + "VALUES (?, ?, 'منطقة', 'Zone', 'مدينة', 'City', true, 3000)", zone, "z-" + zone.toString().substring(0, 8));
        jdbc.update("INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES (?, ?)", kitchen, zone);
        UUID otherKitchen = newKitchen();

        UUID available = published(kitchen, "متاح " + token, 5000);
        UUID soldOut = published(kitchen, "نفد " + token, 5000);
        UUID noSlot = published(kitchen, "بدون موعد " + token, 5000);
        UUID elsewhere = published(otherKitchen, "مطبخ آخر " + token, 5000);
        LocalDate day = daysAhead(3);
        UUID slotA = slot(kitchen, day, 13, 10, 3, available, 5, 2, soldOut, 3, 3);
        slot(kitchen, daysAhead(4), 13, 10, 0, available, 4, 0);
        slot(otherKitchen, day, 13, 10, 0, elsewhere, 4, 0);

        assertThat(allIds(get("/api/v1/meals"), "q", token, "zoneId", zone.toString()))
                .containsExactlyInAnyOrder(available.toString(), soldOut.toString(), noSlot.toString());
        JsonNode onDay = ok(get("/api/v1/meals").param("q", token).param("zoneId", zone.toString()).param("date", day.toString()));
        List<String> dayIds = new ArrayList<>();
        onDay.get("items").forEach(m -> dayIds.add(m.get("id").asString()));
        assertThat(dayIds).containsExactlyInAnyOrder(available.toString(), soldOut.toString());
        for (JsonNode m : onDay.get("items")) {
            JsonNode slots = m.get("nextSlots");
            assertThat(slots.size()).as("restricted to the chosen date").isEqualTo(1);
            assertThat(slots.get(0).get("fulfillmentModes").toString()).isEqualTo("[\"PICKUP\",\"DELIVERY\"]");
            if (m.get("id").asString().equals(available.toString())) {
                assertThat(m.get("availability").asString()).isEqualTo("AVAILABLE");
                assertThat(slots.get(0).get("availablePortions").asInt()).isEqualTo(3);
            } else {
                assertThat(m.get("availability").asString()).isEqualTo("SOLD_OUT");
                assertThat(slots.get(0).get("availablePortions").asInt()).isZero();
            }
        }
        assertThat(allIds(get("/api/v1/meals"), "q", token, "slotId", slotA.toString()))
                .containsExactlyInAnyOrder(available.toString(), soldOut.toString());

        JsonNode all = ok(get("/api/v1/meals").param("q", token).param("limit", "100"));
        for (JsonNode m : all.get("items")) {
            if (m.get("id").asString().equals(noSlot.toString())) {
                assertThat(m.get("availability").asString()).isEqualTo("UNAVAILABLE");
                assertThat(m.get("nextSlots").size()).isZero();
            }
            if (m.get("id").asString().equals(available.toString())) {
                assertThat(m.get("nextSlots").size()).isEqualTo(2);
            }
        }
        JsonNode detail = ok(get("/api/v1/meals/" + available));
        assertThat(detail.get("availability").asString()).isEqualTo("AVAILABLE");
        assertThat(detail.get("nextSlots").size()).isEqualTo(2);

        // A slot whose cutoff passed no longer makes the meal serviceable for its date.
        jdbc.update("UPDATE cook_slot SET starts_at = now() + interval '1 hour', ends_at = now() + interval '2 hours', "
                + "cutoff_at = now() - interval '1 hour', local_date = ? WHERE id = ?", day, slotA);
        assertThat(ok(get("/api/v1/meals").param("q", token).param("date", day.toString())).get("items").size()).as("past cutoff").isEqualTo(1);
    }

    @Test
    void many_slots_are_capped_at_five_in_start_order() throws Exception {
        UUID m = published(kitchen, "كثير المواعيد " + token, 5000);
        for (int d = 1; d <= 7; d++) {
            slot(kitchen, daysAhead(d), 12, 10, 0, m, 5, 0);
        }
        JsonNode item = ok(get("/api/v1/meals").param("q", token)).get("items").get(0);
        assertThat(item.get("nextSlots").size()).isEqualTo(5);
        List<String> starts = new ArrayList<>();
        item.get("nextSlots").forEach(s -> starts.add(s.get("startsAt").asString()));
        assertThat(starts).isSorted();
    }

    // ---- search normalization and localization ----

    @Test
    void arabic_search_ignores_diacritics_and_letter_variants_and_english_titles_are_searchable() throws Exception {
        UUID koshari = meal(kitchen, "PUBLISHED", "كُشَري بالعدس " + token, "Lentil Koshari", 6000, null, Instant.now());
        UUID ful = meal(kitchen, "PUBLISHED", "فول مدمس " + token, null, 3000, null, Instant.now());
        assertThat(allIds(get("/api/v1/meals"), "q", "كشرى بالعدس " + token)).containsExactly(koshari.toString());
        assertThat(allIds(get("/api/v1/meals"), "kitchenId", kitchen.toString(), "q", "lentil koshari")).containsExactly(koshari.toString());
        assertThat(allIds(get("/api/v1/meals"), "kitchenId", kitchen.toString(), "q", "Synthetic Kitchen"))
                .as("kitchen name matches").containsExactlyInAnyOrder(koshari.toString(), ful.toString());
        assertThat(allIds(get("/api/v1/meals"), "kitchenId", kitchen.toString(), "q", "%")).as("wildcards are literal").isEmpty();

        JsonNode en = ok(get("/api/v1/meals").param("kitchenId", kitchen.toString()).param("sort", "PRICE_DESC").header("Accept-Language", "en"));
        assertThat(en.get("items").get(0).get("title").asString()).isEqualTo("Lentil Koshari");
        assertThat(en.get("items").get(1).get("title").asString()).as("Arabic fallback").startsWith("فول مدمس");
        jdbc.update("UPDATE meal SET description_en = 'Synthetic lentil koshari description' WHERE id = ?", koshari);
        JsonNode described = ok(get("/api/v1/meals").param("kitchenId", kitchen.toString()).param("sort", "PRICE_DESC").header("Accept-Language", "en"));
        assertThat(described.get("items").get(0).get("description").asString()).as("D12: the meal's own description, localized")
                .isEqualTo("Synthetic lentil koshari description");
        assertThat(described.get("items").get(1).get("description").asString()).as("Arabic description fallback").isEqualTo("وصف تجريبي للوجبة");
        String text = en.toString();
        assertThat(text).doesNotContain("Synthetic fixture location").doesNotContain("+20100").doesNotContain("\"status\"");
    }

    // ---- API-002 zones ----

    @Test
    void zones_list_active_zones_localized_with_stable_paging() throws Exception {
        String prefix = "zz-" + token.substring(3, 9);
        for (int i = 0; i < 3; i++) {
            jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                    + "VALUES (?, ?, 'منطقة', 'Zone', 'القاهرة', 'Cairo', true, 2500)", UUID.randomUUID(), prefix + "-" + i);
        }
        jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                + "VALUES (?, ?, 'منطقة', 'Zone', 'القاهرة', 'Cairo', false, 2500)", UUID.randomUUID(), prefix + "-off");
        List<String> codes = new ArrayList<>();
        String cursor = null;
        do {
            MockHttpServletRequestBuilder r = get("/api/v1/zones").param("limit", "2").header("Accept-Language", "en");
            if (cursor != null) {
                r = r.param("cursor", cursor);
            }
            JsonNode page = ok(r);
            page.get("items").forEach(z -> {
                codes.add(z.get("code").asString());
                assertThat(z.get("deliveryFee").get("currency").asString()).isEqualTo("EGP");
            });
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);
        assertThat(codes).contains(prefix + "-0", prefix + "-1", prefix + "-2").doesNotContain(prefix + "-off").doesNotHaveDuplicates().isSorted();
    }
}
