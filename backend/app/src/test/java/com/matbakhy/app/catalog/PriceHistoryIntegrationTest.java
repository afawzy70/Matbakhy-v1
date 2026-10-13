package com.matbakhy.app.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.matbakhy.app.security.TestLogins;
import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import com.matbakhy.app.support.TestKitchens;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * D14 / TC-153 (MB-048): the kitchen sees its meal's active price and the previous actual price changes, derived from the
 * immutable revisions; an edit that kept the price adds no entry; another kitchen's meal is not found.
 */
@IntegrationTest
@AutoConfigureMockMvc
@Import(TestLogins.class)
class PriceHistoryIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired TestLogins logins;
    @Autowired JsonMapper json;
    @Autowired Clock clock;

    @Test
    void active_price_and_actual_changes_newest_first_without_entries_for_other_edits_and_owner_only() throws Exception {
        TestCommerce fx = new TestCommerce(jdbc, clock);
        String cookSubject = UUID.randomUUID().toString();
        UUID reviewer = logins.login(UUID.randomUUID().toString(), "Reviewer").getPrincipal().id();
        UUID kitchen = TestKitchens.approvedKitchen(jdbc, logins.login(cookSubject, "Cook").getPrincipal().id(), reviewer);
        UUID meal = fx.meal(kitchen, 12000, "كشري", "Koshari");
        // The kitchen edit's database effect: a description-only edit (same price), then two price changes.
        revision(meal, 2, 12000, "1 minute");
        revision(meal, 3, 13000, "2 minutes");
        revision(meal, 4, 11500, "3 minutes");
        jdbc.update("UPDATE meal SET price_minor = 11500, revision = 4, version = version + 3 WHERE id = ?", meal);

        MockHttpServletResponse r = mvc.perform(get("/api/v1/cook/meals/" + meal + "/price-history")
                .with(authentication(logins.login(cookSubject, "Cook")))).andReturn().getResponse();
        assertThat(r.getStatus()).as(r.getContentAsString()).isEqualTo(200);
        JsonNode body = json.readTree(r.getContentAsString());
        assertThat(body.get("activePrice").get("amountMinor").asLong()).isEqualTo(11500);
        assertThat(body.get("activePrice").get("currency").asString()).isEqualTo("EGP");
        List<String> changes = new ArrayList<>();
        body.get("changes").forEach(c -> changes.add(c.get("revision").asInt() + ":" + c.get("price").get("amountMinor").asLong()));
        assertThat(changes).as("newest first; the same-price edit (revision 2) adds nothing").containsExactly("4:11500", "3:13000", "1:12000");
        assertThat(body.get("changes").get(0).get("changedAt").asString()).endsWith("Z");
        assertThat(r.getContentAsString()).doesNotContain("description").doesNotContain("moderation");

        String otherCook = UUID.randomUUID().toString();
        TestKitchens.approvedKitchen(jdbc, logins.login(otherCook, "Other cook").getPrincipal().id(), reviewer);
        assertThat(mvc.perform(get("/api/v1/cook/meals/" + meal + "/price-history").with(authentication(logins.login(otherCook, "Other cook"))))
                .andReturn().getResponse().getStatus()).as("another kitchen's meal").isEqualTo(404);
        assertThat(mvc.perform(get("/api/v1/cook/meals/" + meal + "/price-history")
                .with(authentication(logins.login(UUID.randomUUID().toString(), "Customer")))).andReturn().getResponse().getStatus())
                .as("an account without a kitchen").isEqualTo(403);
    }

    private void revision(UUID meal, int revision, long price, String after) {
        jdbc.update("""
                INSERT INTO meal_revision (meal_id, revision, snapshot, price_minor, created_at)
                SELECT meal_id, ?, jsonb_set(snapshot, '{priceMinor}', to_jsonb(?::bigint)), ?, created_at + ?::interval
                FROM meal_revision WHERE meal_id = ? AND revision = 1""", revision, price, price, after, meal);
    }
}
