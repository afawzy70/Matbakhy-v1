package com.matbakhy.app.catalog;

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
import com.matbakhy.app.support.TestKitchens;
import com.matbakhy.app.support.TestUploads;
import com.matbakhy.catalog.internal.media.MediaService;
import com.matbakhy.catalog.internal.meal.MealService;
import com.matbakhy.catalog.internal.meal.MealService.MealContent;
import com.matbakhy.shared.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * TC-011, TC-012, TC-114 on PostgreSQL and the S3 emulator: meal drafts, publication guards (declarations,
 * approved photo, selling kitchen), immutable revisions, archive semantics, owner scope and the public DTO.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class MealIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired MealService meals;
    @Autowired MediaService media;

    String cookSubject;
    String otherCookSubject;
    String customerSubject;
    UUID kitchen;
    UUID otherKitchen;

    @BeforeEach
    void setUp() {
        cookSubject = UUID.randomUUID().toString();
        otherCookSubject = UUID.randomUUID().toString();
        customerSubject = UUID.randomUUID().toString();
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        kitchen = TestKitchens.approvedKitchen(jdbc, id(cookSubject), reviewer);
        otherKitchen = TestKitchens.approvedKitchen(jdbc, id(otherCookSubject), reviewer);
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

    private JsonNode body(MockHttpServletResponse r) throws Exception {
        return json.readTree(r.getContentAsString());
    }

    private JsonNode ok(MockHttpServletResponse r, int status) throws Exception {
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(status);
        return body(r);
    }

    private String complete(UUID photo) {
        return """
                {"titleAr":"كشري بيتي","titleEn":"Home koshari","descriptionAr":"كشري مصري بالعدس والأرز والمكرونة","portionAr":"علبة 500 جم",
                 "ingredientsAr":"أرز، عدس، مكرونة، صلصة طماطم، بصل","allergenDeclaration":"CONTAINS","allergenCodes":["GLUTEN"],
                 "priceMinor":9500,"photos":[{"mediaId":"%s","altAr":"طبق كشري"}]}""".formatted(photo);
    }

    private JsonNode publication(String subject, UUID meal, String action, long version, int expected) throws Exception {
        return ok(call(post("/api/v1/cook/meals/" + meal + "/publication").content(
                "{\"action\":\"" + action + "\",\"expectedVersion\":" + version + "}"), as(subject)), expected);
    }

    private JsonNode publishedMeal() throws Exception {
        UUID photo = TestUploads.approvedMealPhoto(mvc, json, as(cookSubject));
        JsonNode created = ok(call(post("/api/v1/cook/meals").content(complete(photo)), as(cookSubject)), 201);
        return publication(cookSubject, UUID.fromString(created.get("id").asString()), "PUBLISH", created.get("version").asLong(), 200);
    }

    // ---- TC-011: declarations and approval enforced ----

    @Test
    void only_approved_cooks_manage_meals() throws Exception {
        assertThat(call(post("/api/v1/cook/meals").content("{\"titleAr\":\"وجبة\"}"), as(customerSubject)).getStatus()).isEqualTo(403);
        assertThat(call(get("/api/v1/cook/meals"), as(customerSubject)).getStatus()).isEqualTo(403);
        assertThat(call(post("/api/v1/cook/meals").content("{\"titleAr\":\"وجبة\"}"), null).getStatus()).isEqualTo(401);
    }

    @Test
    void draft_without_allergen_declaration_cannot_be_published_and_stays_private() throws Exception {
        UUID photo = TestUploads.approvedMealPhoto(mvc, json, as(cookSubject));
        String noDeclaration = complete(photo).replace("\"allergenDeclaration\":\"CONTAINS\",\"allergenCodes\":[\"GLUTEN\"],", "");
        JsonNode draft = ok(call(post("/api/v1/cook/meals").content(noDeclaration), as(cookSubject)), 201);
        UUID meal = UUID.fromString(draft.get("id").asString());
        assertThat(draft.get("status").asString()).isEqualTo("DRAFT");
        assertThat(draft.get("revision").asInt()).isEqualTo(1);

        MockHttpServletResponse r = call(post("/api/v1/cook/meals/" + meal + "/publication").content(
                "{\"action\":\"PUBLISH\",\"expectedVersion\":" + draft.get("version").asLong() + "}"), as(cookSubject));
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).contains("\"field\":\"allergenDeclaration\",\"code\":\"REQUIRED\"");
        assertThat(jdbc.queryForObject("SELECT status FROM meal WHERE id = ?", String.class, meal)).isEqualTo("DRAFT");
        assertThat(call(get("/api/v1/meals/" + meal), null).getStatus()).isEqualTo(404);
    }

    @Test
    void every_publication_requirement_is_named_and_invalid_declarations_are_refused() throws Exception {
        JsonNode bare = ok(call(post("/api/v1/cook/meals").content("{\"titleAr\":\"وجبة\"}"), as(cookSubject)), 201);
        MockHttpServletResponse r = call(post("/api/v1/cook/meals/" + bare.get("id").asString() + "/publication").content(
                "{\"action\":\"PUBLISH\",\"expectedVersion\":" + bare.get("version").asLong() + "}"), as(cookSubject));
        assertThat(r.getStatus()).isEqualTo(422);
        List<String> fields = new ArrayList<>();
        body(r).get("fieldErrors").forEach(e -> fields.add(e.get("field").asString() + ":" + e.get("code").asString()));
        assertThat(fields).containsExactlyInAnyOrder("descriptionAr:REQUIRED", "portionAr:REQUIRED", "ingredientsAr:REQUIRED",
                "allergenDeclaration:REQUIRED", "priceMinor:REQUIRED", "photos:APPROVED_PHOTO_REQUIRED");

        assertThat(call(post("/api/v1/cook/meals").content("{\"allergenDeclaration\":\"CONTAINS\"}"), as(cookSubject)).getContentAsString())
                .contains("REQUIRED_FOR_CONTAINS");
        assertThat(call(post("/api/v1/cook/meals").content("{\"allergenDeclaration\":\"NONE_DECLARED\",\"allergenCodes\":[\"MILK\"]}"),
                as(cookSubject)).getContentAsString()).contains("ONLY_WITH_CONTAINS");
        assertThat(call(post("/api/v1/cook/meals").content("{\"priceMinor\":0}"), as(cookSubject)).getStatus()).isEqualTo(422);
        assertThat(call(post("/api/v1/cook/meals").content("{\"priceMinor\":95.5}"), as(cookSubject)).getStatus())
                .as("fractional money is malformed").isEqualTo(400);
        assertThat(call(post("/api/v1/cook/meals").content("{\"allergenCodes\":[\"PEPPER\"]}"), as(cookSubject)).getStatus()).isEqualTo(400);
        assertThat(call(post("/api/v1/cook/meals").content("{\"titleAr\":\"وجبة\",\"kitchenId\":\"" + otherKitchen + "\"}"),
                as(cookSubject)).getStatus()).as("no kitchen assignment from the client").isEqualTo(400);
    }

    @Test
    void photos_must_be_the_cooks_own_approved_uploads() throws Exception {
        UUID othersPhoto = TestUploads.approvedMealPhoto(mvc, json, as(otherCookSubject));
        MockHttpServletResponse r = call(post("/api/v1/cook/meals").content(complete(othersPhoto)), as(cookSubject));
        assertThat(r.getStatus()).isEqualTo(422);
        assertThat(r.getContentAsString()).contains("PHOTO_NOT_APPROVED");
        String sha = "0".repeat(64);
        JsonNode pending = body(call(post("/api/v1/uploads").content("{\"purpose\":\"MEAL_PHOTO\",\"filename\":\"a.jpg\",\"contentType\":"
                + "\"image/jpeg\",\"bytes\":100,\"sha256\":\"" + sha + "\"}"), as(cookSubject)));
        assertThat(call(post("/api/v1/cook/meals").content(complete(UUID.fromString(pending.get("uploadId").asString()))), as(cookSubject))
                .getStatus()).isEqualTo(422);
    }

    @Test
    void published_meal_has_a_public_dto_without_private_or_internal_fields() throws Exception {
        JsonNode published = publishedMeal();
        assertThat(published.get("status").asString()).isEqualTo("PUBLISHED");
        UUID meal = UUID.fromString(published.get("id").asString());
        MockHttpServletResponse r = call(get("/api/v1/meals/" + meal), null);
        JsonNode pub = ok(r, 200);
        assertThat(pub.get("title").asString()).as("Arabic by default").isEqualTo("كشري بيتي");
        assertThat(pub.get("description").asString()).contains("كشري");
        assertThat(pub.get("allergenCodes").get(0).asString()).isEqualTo("GLUTEN");
        assertThat(pub.get("price").get("amountMinor").asLong()).isEqualTo(9500);
        assertThat(pub.get("price").get("currency").asString()).isEqualTo("EGP");
        assertThat(pub.get("photo").get("url").asString()).contains("/matbakhy-public/media/");
        assertThat(pub.get("photos").get(0).get("alt").asString()).isEqualTo("طبق كشري");
        assertThat(pub.get("kitchen").get("slug").asString()).startsWith("kitchen-");
        assertThat(pub.get("kitchen").get("ratingSummary").get("count").asInt()).isZero();
        assertThat(pub.get("nextSlots").size()).as("slots arrive with MB-015").isZero();
        assertThat(pub.get("availability").asString()).isEqualTo("UNAVAILABLE");
        assertThat(pub.get("publishedRevision").asInt()).isEqualTo(1);
        assertThat(String.join(",", r.getHeaders("Vary"))).contains("Accept-Language");
        String text = r.getContentAsString();
        assertThat(text).doesNotContain(id(cookSubject).toString()).doesNotContain("Synthetic fixture location")
                .doesNotContain("+20100").doesNotContain("\"status\"").doesNotContain("\"version\"").doesNotContain("matbakhy-private");

        JsonNode english = ok(call(get("/api/v1/meals/" + meal).header("Accept-Language", "en-GB,en;q=0.9,ar;q=0.5"), null), 200);
        assertThat(english.get("title").asString()).isEqualTo("Home koshari");
        assertThat(english.get("portionText").asString()).as("falls back to Arabic when no English text").isEqualTo("علبة 500 جم");
        assertThat(ok(call(get("/api/v1/meals/" + meal).header("Accept-Language", "fr"), null), 200).get("title").asString())
                .isEqualTo("كشري بيتي");

        JsonNode none = ok(call(patch("/api/v1/cook/meals/" + meal).content("{\"allergenDeclaration\":\"NONE_DECLARED\",\"expectedVersion\":"
                + published.get("version").asLong() + "}"), as(cookSubject)), 200);
        assertThat(none.get("content").get("allergenCodes").size()).isZero();
        assertThat(ok(call(get("/api/v1/meals/" + meal), null), 200).get("allergenCodes").get(0).asString()).isEqualTo("NONE_DECLARED");
    }

    // ---- TC-012: versioning and archive semantics ----

    @Test
    void edits_create_new_revisions_while_earlier_snapshots_stay_unchanged() throws Exception {
        JsonNode published = publishedMeal();
        UUID meal = UUID.fromString(published.get("id").asString());
        assertThat(published.get("revision").asInt()).isEqualTo(1);

        JsonNode edited = ok(call(patch("/api/v1/cook/meals/" + meal).content("{\"priceMinor\":11000,\"allergenDeclaration\":\"CONTAINS\","
                + "\"allergenCodes\":[\"GLUTEN\",\"SESAME\"],\"expectedVersion\":" + published.get("version").asLong() + "}"), as(cookSubject)), 200);
        assertThat(edited.get("revision").asInt()).isEqualTo(2);
        assertThat(edited.get("status").asString()).as("published meal stays published").isEqualTo("PUBLISHED");

        MealContent first = meals.revision(meal, 1);
        MealContent second = meals.revision(meal, 2);
        assertThat(first.priceMinor()).isEqualTo(9500);
        assertThat(first.allergenCodes()).extracting(Enum::name).containsExactly("GLUTEN");
        assertThat(second.priceMinor()).isEqualTo(11000);
        assertThat(second.allergenCodes()).extracting(Enum::name).containsExactly("GLUTEN", "SESAME");
        assertThat(body(call(get("/api/v1/meals/" + meal), null)).get("price").get("amountMinor").asLong()).isEqualTo(11000);

        MockHttpServletResponse stale = call(patch("/api/v1/cook/meals/" + meal).content("{\"priceMinor\":12000,\"expectedVersion\":"
                + published.get("version").asLong() + "}"), as(cookSubject));
        assertThat(stale.getStatus()).isEqualTo(409);
        assertThat(stale.getContentAsString()).contains("VERSION_CONFLICT");
        JsonNode same = ok(call(patch("/api/v1/cook/meals/" + meal).content("{\"priceMinor\":11000,\"expectedVersion\":"
                + edited.get("version").asLong() + "}"), as(cookSubject)), 200);
        assertThat(same.get("revision").asInt()).as("no-op edit adds no revision").isEqualTo(2);

        MockHttpServletResponse incomplete = call(patch("/api/v1/cook/meals/" + meal).content("{\"photos\":[],\"expectedVersion\":"
                + same.get("version").asLong() + "}"), as(cookSubject));
        assertThat(incomplete.getStatus()).as("a published meal cannot lose required content").isEqualTo(422);

        assertThatThrownBy(() -> jdbc.update("UPDATE meal_revision SET price_minor = 1 WHERE meal_id = ?", meal)).hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM meal_revision WHERE meal_id = ?", meal)).hasMessageContaining("append-only");
    }

    @Test
    void archive_removes_the_meal_from_sale_without_touching_history() throws Exception {
        JsonNode published = publishedMeal();
        UUID meal = UUID.fromString(published.get("id").asString());
        JsonNode archived = publication(cookSubject, meal, "ARCHIVE", published.get("version").asLong(), 200);
        assertThat(archived.get("status").asString()).isEqualTo("ARCHIVED");
        assertThat(call(get("/api/v1/meals/" + meal), null).getStatus()).isEqualTo(404);
        assertThat(call(patch("/api/v1/cook/meals/" + meal).content("{\"priceMinor\":1,\"expectedVersion\":"
                + archived.get("version").asLong() + "}"), as(cookSubject)).getStatus()).isEqualTo(409);
        assertThat(call(post("/api/v1/cook/meals/" + meal + "/publication").content("{\"action\":\"PUBLISH\",\"expectedVersion\":"
                + archived.get("version").asLong() + "}"), as(cookSubject)).getStatus()).isEqualTo(409);
        assertThat(meals.revision(meal, 1).priceMinor()).isEqualTo(9500);
        assertThatThrownBy(() -> jdbc.update("DELETE FROM meal WHERE id = ?", meal)).hasMessageContaining("archived, not deleted");
    }

    @Test
    void pause_and_republish_and_photo_deletion_is_refused_while_in_use() throws Exception {
        JsonNode published = publishedMeal();
        UUID meal = UUID.fromString(published.get("id").asString());
        JsonNode paused = publication(cookSubject, meal, "PAUSE", published.get("version").asLong(), 200);
        assertThat(call(get("/api/v1/meals/" + meal), null).getStatus()).isEqualTo(404);
        JsonNode again = publication(cookSubject, meal, "PUBLISH", paused.get("version").asLong(), 200);
        assertThat(call(get("/api/v1/meals/" + meal), null).getStatus()).isEqualTo(200);

        UUID photo = UUID.fromString(again.get("content").get("photos").get(0).get("mediaId").asString());
        UUID cook = id(cookSubject);
        assertThatThrownBy(() -> media.delete(cook, photo)).isInstanceOf(ApiException.class).hasMessageContaining("preserved record");
        publication(cookSubject, meal, "ARCHIVE", again.get("version").asLong(), 200);
        media.delete(cook, photo);
    }

    // ---- kitchen eligibility and scope ----

    @Test
    void a_kitchen_that_cannot_sell_cannot_publish_and_its_meals_are_not_public() throws Exception {
        JsonNode published = publishedMeal();
        UUID meal = UUID.fromString(published.get("id").asString());
        jdbc.update("UPDATE kitchen SET operating_state = 'SUSPENDED', state_reason = 'test' WHERE id = ?", kitchen);
        assertThat(call(get("/api/v1/meals/" + meal), null).getStatus()).isEqualTo(404);
        JsonNode paused = publication(cookSubject, meal, "PAUSE", published.get("version").asLong(), 200);
        MockHttpServletResponse r = call(post("/api/v1/cook/meals/" + meal + "/publication").content(
                "{\"action\":\"PUBLISH\",\"expectedVersion\":" + paused.get("version").asLong() + "}"), as(cookSubject));
        assertThat(r.getStatus()).isEqualTo(409);
        assertThat(r.getContentAsString()).contains("KITCHEN_UNAVAILABLE");
        jdbc.update("UPDATE kitchen SET operating_state = 'ACTIVE', state_reason = NULL WHERE id = ?", kitchen);
        publication(cookSubject, meal, "PUBLISH", paused.get("version").asLong(), 200);
        assertThat(call(get("/api/v1/meals/" + meal), null).getStatus()).isEqualTo(200);
    }

    @Test
    void cooks_only_see_and_change_their_own_meals() throws Exception {
        JsonNode mine = ok(call(post("/api/v1/cook/meals").content("{\"titleAr\":\"وجبة أولى\"}"), as(cookSubject)), 201);
        for (int i = 0; i < 3; i++) {
            ok(call(post("/api/v1/cook/meals").content("{\"titleAr\":\"وجبة رقم " + i + "\"}"), as(cookSubject)), 201);
        }
        ok(call(post("/api/v1/cook/meals").content("{\"titleAr\":\"وجبة الطاهي الآخر\"}"), as(otherCookSubject)), 201);
        UUID meal = UUID.fromString(mine.get("id").asString());
        assertThat(call(patch("/api/v1/cook/meals/" + meal).content("{\"titleAr\":\"اختطاف\",\"expectedVersion\":0}"), as(otherCookSubject))
                .getStatus()).isEqualTo(404);
        assertThat(call(post("/api/v1/cook/meals/" + meal + "/publication").content("{\"action\":\"ARCHIVE\",\"expectedVersion\":0}"),
                as(otherCookSubject)).getStatus()).isEqualTo(404);

        List<String> seen = new ArrayList<>();
        String cursor = null;
        do {
            JsonNode page = ok(call(get("/api/v1/cook/meals").param("limit", "2").param("cursor", cursor), as(cookSubject)), 200);
            page.get("items").forEach(m -> {
                assertThat(m.get("kitchenId").asString()).isEqualTo(kitchen.toString());
                seen.add(m.get("id").asString());
            });
            cursor = page.get("nextCursor").isNull() ? null : page.get("nextCursor").asString();
        } while (cursor != null);
        assertThat(seen).hasSize(4).doesNotHaveDuplicates();
        assertThat(ok(call(get("/api/v1/cook/meals").param("q", "أولى"), as(cookSubject)), 200).get("items").size()).isEqualTo(1);
        assertThat(ok(call(get("/api/v1/cook/meals").param("status", "PUBLISHED"), as(cookSubject)), 200).get("items").size()).isZero();
    }
}
