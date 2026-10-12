package com.matbakhy.app.orders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.TestCommerce;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * API-001 (Q1 option A): clients learn the active commerce policy's per-line limit instead of hard-coding one. The
 * server stays the enforcement authority (QuoteIntegrationTest); this endpoint only publishes the limit, without
 * commission, tax or any staff-only policy field.
 */
@IntegrationTest
@AutoConfigureMockMvc
class PublicConfigurationIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired Clock clock;

    TestCommerce fx;

    @BeforeEach
    void setUp() {
        fx = new TestCommerce(jdbc, clock);
    }

    @AfterEach
    void reopenOrdering() {
        jdbc.update("UPDATE feature_flag SET enabled = true WHERE key = 'ordering-enabled'");
    }

    private JsonNode publicConfiguration() throws Exception {
        MockHttpServletResponse r = mvc.perform(get("/api/v1/configuration/public")).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        return json.readTree(r.getContentAsString());
    }

    @Test
    void anyone_can_read_the_active_per_line_limit_and_nothing_commercial_beyond_it() throws Exception {
        int version = fx.activatePolicy(1400);
        JsonNode body = publicConfiguration();
        assertThat(body.get("orderingAvailable").asBoolean()).isTrue();
        assertThat(body.get("orderLimits").get("maxQuantityPerLine").asInt()).isEqualTo(10);
        assertThat(body.get("orderLimits").get("maxLinesPerOrder").asInt()).isEqualTo(20);
        assertThat(body.get("orderLimits").get("policyVersion").asInt()).isEqualTo(version);
        String raw = body.toString().toLowerCase();
        assertThat(raw).doesNotContain("commission").doesNotContain("tax").doesNotContain("synthetic").doesNotContain("approval");
    }

    @Test
    void a_newly_activated_policy_is_published_at_once() throws Exception {
        fx.activatePolicy(0);
        int version = fx.activatePolicy(0);
        jdbc.update("UPDATE commerce_policy SET max_item_quantity = 6 WHERE version = ?", version);
        JsonNode limits = publicConfiguration().get("orderLimits");
        assertThat(limits.get("maxQuantityPerLine").asInt()).isEqualTo(6);
        assertThat(limits.get("policyVersion").asInt()).isEqualTo(version);
    }

    @Test
    void while_ordering_is_paused_no_limits_are_offered() throws Exception {
        fx.activatePolicy(0);
        jdbc.update("UPDATE feature_flag SET enabled = false WHERE key = 'ordering-enabled'");
        JsonNode body = publicConfiguration();
        assertThat(body.get("orderingAvailable").asBoolean()).isFalse();
        assertThat(body.get("orderLimits").isNull()).isTrue();
    }
}
