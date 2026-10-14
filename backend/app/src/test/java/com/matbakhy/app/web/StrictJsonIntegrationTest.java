package com.matbakhy.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.app.support.ProbeSecurity;
import com.matbakhy.shared.Money;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** TC-107 (backend): malformed monetary DTOs are rejected rather than coerced or truncated. */
@IntegrationTest
@Import({StrictJsonIntegrationTest.MoneyProbe.class, ProbeSecurity.class})
class StrictJsonIntegrationTest {

    @LocalServerPort
    int port;

    record PriceUpdate(Money price, int quantity) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class MoneyProbe {
        @PostMapping("/api/v1/test-money")
        PriceUpdate echo(@RequestBody PriceUpdate update) {
            return update;
        }
    }

    private HttpResponse<String> post(String json) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/v1/test-money"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json)).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void well_formed_money_round_trips_as_integer_minor_units() throws Exception {
        HttpResponse<String> r = post("{\"price\":{\"amountMinor\":27000,\"currency\":\"EGP\"},\"quantity\":2}");
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).isEqualTo("{\"price\":{\"amountMinor\":27000,\"currency\":\"EGP\"},\"quantity\":2}");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"price\":{\"amountMinor\":270.5,\"currency\":\"EGP\"},\"quantity\":2}",
            "{\"price\":{\"amountMinor\":\"27000\",\"currency\":\"EGP\"},\"quantity\":2}",
            "{\"price\":{\"amountMinor\":\"270.00\",\"currency\":\"EGP\"},\"quantity\":2}",
            "{\"price\":{\"amountMinor\":27000},\"quantity\":2}",
            "{\"price\":{\"currency\":\"EGP\"},\"quantity\":2}",
            "{\"price\":{\"amountMinor\":27000,\"currency\":\"egp\"},\"quantity\":2}",
            "{\"price\":{\"amountMinor\":27000,\"currency\":\"EGP\",\"formatted\":\"EGP 270\"},\"quantity\":2}",
            "{\"price\":{\"amountMinor\":27000,\"currency\":\"EGP\"},\"quantity\":2,\"status\":\"COMPLETED\"}",
            "{\"price\":{\"amountMinor\":27000,\"currency\":\"EGP\"},\"quantity\":2.7}",
            "{\"price\":{\"amountMinor\":27000,\"currency\":\"EGP\"},\"quantity\":null}",
            "{\"price\":{\"amountMinor\":true,\"currency\":\"EGP\"},\"quantity\":2}",
            "{\"price\":{\"amountMinor\":99999999999999999999,\"currency\":\"EGP\"},\"quantity\":2}"
    })
    void malformed_money_or_unexpected_fields_are_rejected(String json) throws Exception {
        HttpResponse<String> r = post(json);
        assertThat(r.statusCode()).as(json).isEqualTo(400);
        assertThat(r.body()).contains("\"code\":\"MALFORMED_REQUEST\"");
    }
}
