package com.matbakhy.app.availability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.matbakhy.app.security.AccountAuthentication;
import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.MutableClock;
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.availability.api.SlotDirectory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.zone.ZoneOffsetTransition;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TC-013, TC-014, TC-015, TC-115 on PostgreSQL: Cairo scheduling across daylight-saving changes, cutoff by server
 * time, capacity bounds against obligations at meal and slot level, last-portion arithmetic, pause/close/cancel,
 * owner scope and public slot listing.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import({TestLogins.class, SlotIntegrationTest.Beans.class})
class SlotIntegrationTest {

    static final ZoneId CAIRO = ZoneId.of("Africa/Cairo");

    @TestConfiguration(proxyBeanMethods = false)
    static class Beans {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired SlotDirectory directory;
    @Autowired Clock clock;

    String cookSubject;
    String otherCookSubject;
    UUID kitchen;
    UUID otherKitchen;
    UUID mealA;
    UUID mealB;

    @BeforeEach
    void setUp() {
        ((MutableClock) clock).reset();
        // Start each test at 08:00 Cairo time tomorrow so relative slots never cross local midnight.
        ZonedDateTime morning = ZonedDateTime.ofInstant(clock.instant(), CAIRO).plusDays(1).withHour(8).withMinute(0).withSecond(0).withNano(0);
        ((MutableClock) clock).advance(Duration.between(clock.instant(), morning.toInstant()));
        cookSubject = UUID.randomUUID().toString();
        otherCookSubject = UUID.randomUUID().toString();
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, id(cookSubject), reviewer);
        otherKitchen = TestKitchens.approvedKitchen(jdbc, id(otherCookSubject), reviewer);
        mealA = meal(kitchen);
        mealB = meal(kitchen);
    }

    @AfterEach
    void resetClock() {
        ((MutableClock) clock).reset();
    }

    private UUID meal(UUID kitchenId) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO meal (id, kitchen_id, title_ar) VALUES (?, ?, 'وجبة اختبار')", id, kitchenId);
        return id;
    }

    private AccountAuthentication as(String subject) {
        return logins.login(subject, "User");
    }

    private UUID id(String subject) {
        return as(subject).getPrincipal().id();
    }

    private MockHttpServletResponse call(MockHttpServletRequestBuilder r, AccountAuthentication who) throws Exception {
        if (who != null) {
            r = r.with(authentication(who));
        }
        return mvc.perform(r.with(csrf()).contentType(MediaType.APPLICATION_JSON)).andReturn().getResponse();
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return json.readTree(r.getContentAsString());
    }

    /** Local Cairo start a number of hours from the (test) clock, rounded to the minute. */
    private ZonedDateTime inHours(long hours) {
        return ZonedDateTime.ofInstant(clock.instant().plus(Duration.ofHours(hours)), CAIRO).withSecond(0).withNano(0);
    }

    private String slotJson(ZonedDateTime start, int minutes, int maxPortions, String meals, String extra) {
        ZonedDateTime end = start.plusMinutes(minutes);
        return """
                {"localDate":"%s","localStart":"%s","localEnd":"%s","timezone":"Africa/Cairo","maxPortions":%d,"mealCapacities":[%s]%s}"""
                .formatted(start.toLocalDate(), start.toLocalTime(), end.toLocalTime(), maxPortions, meals, extra);
    }

    private JsonNode openSlot(ZonedDateTime start, int maxPortions, String meals) throws Exception {
        JsonNode slot = ok(call(post("/api/v1/cook/slots").content(slotJson(start, 60, maxPortions, meals, "")), as(cookSubject)), 201);
        assertThat(slot.get("state").asString()).isEqualTo("DRAFT");
        return ok(call(patch("/api/v1/cook/slots/" + slot.get("id").asString()).content(
                "{\"state\":\"OPEN\",\"expectedVersion\":" + slot.get("version").asLong() + "}"), as(cookSubject)), 200);
    }

    private String cap(UUID meal, int capacity) {
        return "{\"mealId\":\"" + meal + "\",\"capacity\":" + capacity + "}";
    }

    private MockHttpServletResponse patchSlot(UUID slot, String body) throws Exception {
        long version = jdbc.queryForObject("SELECT version FROM cook_slot WHERE id = ?", Long.class, slot);
        String withVersion = body.substring(0, body.lastIndexOf('}')) + ",\"expectedVersion\":" + version + "}";
        return call(patch("/api/v1/cook/slots/" + slot).content(withVersion), as(cookSubject));
    }

    // ---- TC-013: capacity reductions below obligations ----

    @Test
    void capacity_cannot_be_reduced_below_what_orders_already_hold() throws Exception {
        JsonNode slot = openSlot(inHours(6), 10, cap(mealA, 5) + "," + cap(mealB, 3));
        UUID id = UUID.fromString(slot.get("id").asString());
        // Five obligated portions of meal A (as order placement/acceptance will record them in MB-018/019).
        jdbc.update("UPDATE meal_slot SET reserved = 3, allocated = 2 WHERE cook_slot_id = ? AND meal_id = ?", id, mealA);
        jdbc.update("UPDATE cook_slot SET reserved = 3, allocated = 2 WHERE id = ?", id);

        MockHttpServletResponse lowerMeal = patchSlot(id, "{\"mealCapacities\":[" + cap(mealA, 4) + "," + cap(mealB, 3) + "]}");
        assertThat(lowerMeal.getStatus()).isEqualTo(409);
        assertThat(lowerMeal.getContentAsString()).contains("BELOW_OBLIGATIONS");
        MockHttpServletResponse lowerSlot = patchSlot(id, "{\"maxPortions\":4}");
        assertThat(lowerSlot.getStatus()).isEqualTo(409);
        assertThat(patchSlot(id, "{\"mealCapacities\":[" + cap(mealB, 3) + "]}").getStatus()).as("removing an obligated meal").isEqualTo(409);
        assertThat(patchSlot(id, "{\"state\":\"CANCELLED\"}").getStatus()).as("cancel needs the ops exception path").isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT capacity FROM meal_slot WHERE cook_slot_id = ? AND meal_id = ?", Integer.class, id, mealA))
                .isEqualTo(5);
        assertThat(jdbc.queryForObject("SELECT max_portions FROM cook_slot WHERE id = ?", Integer.class, id)).isEqualTo(10);

        JsonNode exact = ok(patchSlot(id, "{\"maxPortions\":5,\"mealCapacities\":[" + cap(mealA, 5) + "]}"), 200);
        assertThat(exact.get("availablePortions").asInt()).isZero();
        assertThat(exact.get("meals").size()).as("unobligated meal B removed").isEqualTo(1);
        assertThatThrownBy(() -> jdbc.update("UPDATE meal_slot SET capacity = 4 WHERE cook_slot_id = ?", id))
                .hasMessageContaining("meal_slot_capacity_ck");
        assertThatThrownBy(() -> jdbc.update("UPDATE cook_slot SET max_portions = 4 WHERE id = ?", id))
                .hasMessageContaining("cook_slot_capacity_ck");
    }

    @Test
    void the_last_portion_is_bounded_by_both_the_meal_and_the_slot_aggregate() throws Exception {
        JsonNode slot = openSlot(inHours(6), 6, cap(mealA, 5) + "," + cap(mealB, 5));
        UUID id = UUID.fromString(slot.get("id").asString());
        jdbc.update("UPDATE meal_slot SET reserved = 4 WHERE cook_slot_id = ? AND meal_id = ?", id, mealA);
        jdbc.update("UPDATE meal_slot SET reserved = 1 WHERE cook_slot_id = ? AND meal_id = ?", id, mealB);
        jdbc.update("UPDATE cook_slot SET reserved = 5 WHERE id = ?", id);
        SlotDirectory.SlotStatus status = directory.slot(id).orElseThrow();
        assertThat(status.availablePortions()).isEqualTo(1);
        assertThat(status.availableFor(mealA)).isEqualTo(1);
        assertThat(status.availableFor(mealB)).as("meal B has 4 left but the slot only 1").isEqualTo(1);
        assertThat(status.orderableAt(clock.instant())).isTrue();

        jdbc.update("UPDATE meal_slot SET reserved = 5 WHERE cook_slot_id = ? AND meal_id = ?", id, mealA);
        jdbc.update("UPDATE cook_slot SET reserved = 6 WHERE id = ?", id);
        status = directory.slot(id).orElseThrow();
        assertThat(status.availableFor(mealA)).isZero();
        assertThat(status.availableFor(mealB)).isZero();
        JsonNode pub = ok(call(get("/api/v1/kitchens/" + kitchen + "/slots"), null), 200);
        assertThat(pub.get("items").get(0).get("availablePortions").asInt()).isZero();
        assertThatThrownBy(() -> jdbc.update("UPDATE cook_slot SET reserved = 7 WHERE id = ?", id)).hasMessageContaining("cook_slot_capacity_ck");
    }

    // ---- TC-014: Cairo time and daylight-saving boundaries ----

    private ZoneOffsetTransition nextTransition(boolean gap) {
        Instant from = Instant.now();
        for (int i = 0; i < 6; i++) {
            ZoneOffsetTransition t = CAIRO.getRules().nextTransition(from);
            assertThat(t).as("Africa/Cairo has daylight-saving transitions in the JDK time-zone data").isNotNull();
            if (t.isGap() == gap) {
                return t;
            }
            from = t.getInstant();
        }
        throw new AssertionError("no Cairo " + (gap ? "gap" : "overlap") + " transition found");
    }

    private void clockTenDaysBefore(Instant target) {
        ((MutableClock) clock).advance(Duration.between(clock.instant(), target.minus(Duration.ofDays(10))));
    }

    @Test
    void a_local_time_skipped_by_the_spring_change_is_rejected() throws Exception {
        ZoneOffsetTransition gap = nextTransition(true);
        clockTenDaysBefore(gap.getInstant());
        LocalDateTime skipped = gap.getDateTimeBefore().plusMinutes(30);
        String body = """
                {"localDate":"%s","localStart":"%s","localEnd":"%s","maxPortions":5}"""
                .formatted(skipped.toLocalDate(), skipped.toLocalTime(), skipped.toLocalTime().plusMinutes(90));
        MockHttpServletResponse r = call(post("/api/v1/cook/slots").content(body), as(cookSubject));
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).contains("\"field\":\"localStart\",\"code\":\"NONEXISTENT_LOCAL_TIME\"");
    }

    @Test
    void a_repeated_local_time_needs_an_explicit_offset_and_maps_to_one_instant() throws Exception {
        ZoneOffsetTransition overlap = nextTransition(false);
        clockTenDaysBefore(overlap.getInstant());
        LocalDateTime repeated = overlap.getDateTimeAfter().plusMinutes(15);
        ZoneOffset first = overlap.getOffsetBefore();
        ZoneOffset second = overlap.getOffsetAfter();
        String base = """
                {"localDate":"%s","localStart":"%s","localEnd":"%s","maxPortions":5%%s}"""
                .formatted(repeated.toLocalDate(), repeated.toLocalTime(), repeated.toLocalTime().plusMinutes(30));

        MockHttpServletResponse ambiguous = call(post("/api/v1/cook/slots").content(base.formatted("")), as(cookSubject));
        assertThat(ambiguous.getStatus()).isEqualTo(422);
        assertThat(ambiguous.getContentAsString()).contains("AMBIGUOUS_LOCAL_TIME");
        assertThat(call(post("/api/v1/cook/slots").content(base.formatted(",\"startOffset\":\"+05:00\",\"endOffset\":\"+05:00\"")),
                as(cookSubject)).getContentAsString()).contains("OFFSET_NOT_VALID_FOR_LOCAL_TIME");

        JsonNode earlier = ok(call(post("/api/v1/cook/slots").content(base.formatted(
                ",\"startOffset\":\"" + first + "\",\"endOffset\":\"" + first + "\"")), as(cookSubject)), 201);
        assertThat(Instant.parse(earlier.get("startsAt").asString())).isEqualTo(repeated.toInstant(first));
        JsonNode later = ok(call(post("/api/v1/cook/slots").content(base.formatted(
                ",\"startOffset\":\"" + second + "\",\"endOffset\":\"" + second + "\"")), as(cookSubject)), 201);
        assertThat(Instant.parse(later.get("startsAt").asString())).isEqualTo(repeated.toInstant(second));
        assertThat(Duration.between(Instant.parse(earlier.get("startsAt").asString()), Instant.parse(later.get("startsAt").asString())))
                .as("same wall-clock time, two distinct instants one hour apart").isEqualTo(Duration.ofHours(1));
        assertThat(earlier.get("localDate").asString()).isEqualTo(later.get("localDate").asString());
    }

    @Test
    void ordinary_slots_store_utc_instants_for_cairo_wall_clock_times() throws Exception {
        ZonedDateTime start = inHours(30).withHour(13).withMinute(0);
        JsonNode slot = ok(call(post("/api/v1/cook/slots").content(slotJson(start, 120, 5, cap(mealA, 5), "")), as(cookSubject)), 201);
        assertThat(Instant.parse(slot.get("startsAt").asString())).isEqualTo(start.toInstant());
        assertThat(Instant.parse(slot.get("cutoffAt").asString())).isEqualTo(start.toInstant().minus(Duration.ofMinutes(120)));
        assertThat(slot.get("timezone").asString()).isEqualTo("Africa/Cairo");
        assertThat(call(post("/api/v1/cook/slots").content(slotJson(start.plusDays(1), 60, 5, "", "").replace("Africa/Cairo", "Europe/London")),
                as(cookSubject)).getStatus()).isEqualTo(422);
        String reversed = slotJson(start.plusDays(1), 60, 5, "", "").replaceFirst("\"localEnd\":\"[^\"]+\"", "\"localEnd\":\"11:00\"");
        assertThat(call(post("/api/v1/cook/slots").content(reversed), as(cookSubject)).getContentAsString()).contains("END_NOT_AFTER_START");
        assertThat(call(post("/api/v1/cook/slots").content(slotJson(start.plusMinutes(30), 60, 5, "", "")), as(cookSubject)).getStatus())
                .as("overlapping slot").isEqualTo(409);
    }

    // ---- TC-015: cutoff by server time ----

    @Test
    void server_time_reaching_cutoff_closes_ordering_and_capacity_edits() throws Exception {
        JsonNode slot = openSlot(inHours(3), 10, cap(mealA, 5));
        UUID id = UUID.fromString(slot.get("id").asString());
        assertThat(slot.get("orderable").asBoolean()).isTrue();
        assertThat(ok(call(get("/api/v1/kitchens/" + kitchen + "/slots"), null), 200).get("items").size()).isEqualTo(1);

        ((MutableClock) clock).advance(Duration.ofMinutes(61));
        assertThat(directory.slot(id).orElseThrow().orderableAt(clock.instant())).isFalse();
        assertThat(ok(call(get("/api/v1/kitchens/" + kitchen + "/slots"), null), 200).get("items").size()).isZero();
        MockHttpServletResponse edit = patchSlot(id, "{\"maxPortions\":12}");
        assertThat(edit.getStatus()).isEqualTo(409);
        assertThat(edit.getContentAsString()).contains("SLOT_CLOSED");
        ok(patchSlot(id, "{\"state\":\"PAUSED\"}"), 200);
        assertThat(patchSlot(id, "{\"state\":\"OPEN\"}").getContentAsString()).contains("SLOT_CLOSED");
        ok(patchSlot(id, "{\"state\":\"CLOSED\"}"), 200);
        assertThat(patchSlot(id, "{\"state\":\"OPEN\"}").getStatus()).as("closed is final").isEqualTo(409);

        MockHttpServletResponse late = call(post("/api/v1/cook/slots").content(slotJson(inHours(1), 60, 5, "", "")), as(cookSubject));
        assertThat(late.getStatus()).as("cutoff (start - 120 min) already passed").isEqualTo(409);
        assertThat(late.getContentAsString()).contains("CUTOFF_PASSED");
    }

    // ---- pause/close, scope and public listing ----

    @Test
    void pause_hides_a_slot_and_opening_requires_offered_meals() throws Exception {
        JsonNode empty = ok(call(post("/api/v1/cook/slots").content(slotJson(inHours(8), 60, 5, "", "")), as(cookSubject)), 201);
        MockHttpServletResponse open = patchSlot(UUID.fromString(empty.get("id").asString()), "{\"state\":\"OPEN\"}");
        assertThat(open.getStatus()).isEqualTo(422);
        assertThat(open.getContentAsString()).contains("MEAL_CAPACITY_REQUIRED");

        JsonNode slot = openSlot(inHours(5), 10, cap(mealA, 5));
        UUID id = UUID.fromString(slot.get("id").asString());
        ok(patchSlot(id, "{\"state\":\"PAUSED\"}"), 200);
        assertThat(directory.slot(id).orElseThrow().orderableAt(clock.instant())).isFalse();
        assertThat(ok(call(get("/api/v1/kitchens/" + kitchen + "/slots"), null), 200).get("items").size()).isZero();
        ok(patchSlot(id, "{\"state\":\"OPEN\"}"), 200);
        assertThat(directory.slot(id).orElseThrow().orderableAt(clock.instant())).isTrue();
    }

    @Test
    void meals_must_belong_to_the_kitchen_and_cooks_only_touch_their_own_slots() throws Exception {
        UUID foreign = meal(otherKitchen);
        MockHttpServletResponse r = call(post("/api/v1/cook/slots").content(slotJson(inHours(6), 60, 5, cap(foreign, 2), "")), as(cookSubject));
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).contains("MEAL_NOT_OFFERABLE");
        jdbc.update("UPDATE meal SET status = 'ARCHIVED', archived_at = now() WHERE id = ?", mealB);
        assertThat(call(post("/api/v1/cook/slots").content(slotJson(inHours(6), 60, 5, cap(mealB, 2), "")), as(cookSubject)).getStatus())
                .isEqualTo(422);
        assertThat(call(post("/api/v1/cook/slots").content(slotJson(inHours(6), 60, 5, cap(mealA, 6), "")), as(cookSubject)).getStatus())
                .as("meal capacity above the slot maximum").isEqualTo(422);

        JsonNode slot = openSlot(inHours(6), 10, cap(mealA, 5));
        UUID id = UUID.fromString(slot.get("id").asString());
        assertThat(call(patch("/api/v1/cook/slots/" + id).content("{\"maxPortions\":20,\"expectedVersion\":" + slot.get("version").asLong() + "}"),
                as(otherCookSubject)).getStatus()).isEqualTo(404);
        assertThat(call(get("/api/v1/cook/slots"), as(UUID.randomUUID().toString())).getStatus()).as("not a cook").isEqualTo(403);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO meal_slot (id, cook_slot_id, meal_id, capacity) VALUES (?, ?, ?, 1)",
                UUID.randomUUID(), id, foreign)).hasMessageContaining("different kitchens");
        MockHttpServletResponse stale = call(patch("/api/v1/cook/slots/" + id).content("{\"maxPortions\":9,\"expectedVersion\":0}"), as(cookSubject));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("VERSION_CONFLICT");
    }

    @Test
    void public_listing_shows_only_open_future_slots_of_selling_kitchens_with_their_fulfillment_modes() throws Exception {
        openSlot(inHours(6), 10, cap(mealA, 5));
        ok(call(post("/api/v1/cook/slots").content(slotJson(inHours(9), 60, 5, cap(mealA, 2), "")), as(cookSubject)), 201);
        JsonNode pub = ok(call(get("/api/v1/kitchens/" + kitchen + "/slots"), null), 200);
        assertThat(pub.get("items").size()).as("draft slot hidden").isEqualTo(1);
        assertThat(pub.get("items").get(0).get("fulfillmentModes").toString()).isEqualTo("[\"PICKUP\"]");
        assertThat(ok(call(get("/api/v1/kitchens/" + kitchen + "/slots").param("fulfillmentMode", "DELIVERY"), null), 200).get("items").size())
                .isZero();

        UUID zone = UUID.randomUUID();
        jdbc.update("INSERT INTO service_zone (id, code, name_ar, name_en, city_ar, city_en, active, delivery_fee_minor) "
                + "VALUES (?, ?, 'منطقة', 'Zone', 'مدينة', 'City', true, 3000)", zone, "z-" + zone.toString().substring(0, 8));
        jdbc.update("INSERT INTO kitchen_zone (kitchen_id, zone_id) VALUES (?, ?)", kitchen, zone);
        JsonNode delivery = ok(call(get("/api/v1/kitchens/" + kitchen + "/slots").param("fulfillmentMode", "DELIVERY")
                .param("zoneId", zone.toString()), null), 200);
        assertThat(delivery.get("items").get(0).get("fulfillmentModes").toString()).isEqualTo("[\"DELIVERY\"]");
        assertThat(ok(call(get("/api/v1/kitchens/" + kitchen + "/slots").param("fulfillmentMode", "DELIVERY")
                .param("zoneId", UUID.randomUUID().toString()), null), 200).get("items").size()).as("zone not served").isZero();

        jdbc.update("UPDATE kitchen SET operating_state = 'SUSPENDED' WHERE id = ?", kitchen);
        assertThat(call(get("/api/v1/kitchens/" + kitchen + "/slots"), null).getStatus()).isEqualTo(404);
        assertThat(call(get("/api/v1/kitchens/" + UUID.randomUUID() + "/slots"), null).getStatus()).isEqualTo(404);
    }

    @Test
    void cook_slot_list_pages_by_start_time() throws Exception {
        for (int i = 0; i < 5; i++) {
            ok(call(post("/api/v1/cook/slots").content(slotJson(inHours(5 + i * 2L), 60, 5, "", "")), as(cookSubject)), 201);
        }
        List<String> starts = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = ok(call(get("/api/v1/cook/slots").param("limit", "2").param("cursor", cursor), as(cookSubject)), 200);
            page.get("items").forEach(s -> starts.add(s.get("startsAt").asString()));
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);
        assertThat(starts).hasSize(5).isSorted().doesNotHaveDuplicates();
    }
}
