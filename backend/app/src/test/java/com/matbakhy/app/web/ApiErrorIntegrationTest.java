package com.matbakhy.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.shared.error.ApiException;
import com.matbakhy.shared.error.ErrorCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Standard error body for framework and domain failures, with request ID correlation. */
@IntegrationTest
@Import(ApiErrorIntegrationTest.ProbeController.class)
class ApiErrorIntegrationTest {

    @LocalServerPort
    int port;

    record Probe(@NotBlank String name, @Min(1) int quantity) {
    }

    @TestConfiguration(proxyBeanMethods = false)
    @RestController
    static class ProbeController {
        @PostMapping("/api/v1/test-probe")
        Probe echo(@Valid @RequestBody Probe probe) {
            return probe;
        }

        @GetMapping("/api/v1/test-probe/conflict")
        void conflict() {
            throw new ApiException(ErrorCode.STOCK_CHANGED, "synthetic stock conflict");
        }

        @GetMapping("/api/v1/test-probe/boom")
        void boom() {
            throw new IllegalStateException("internal detail that must not leak: SELECT * FROM secret");
        }
    }

    private HttpResponse<String> send(HttpRequest.Builder builder) throws Exception {
        return HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    private HttpRequest.Builder postJson(String path, String json) {
        return request(path).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(json));
    }

    @Test
    void valid_request_succeeds_and_generates_request_id() throws Exception {
        HttpResponse<String> r = send(postJson("/api/v1/test-probe", "{\"name\":\"x\",\"quantity\":1}"));
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.headers().firstValue("X-Request-Id")).hasValueSatisfying(id -> assertThat(id).hasSize(36));
    }

    @Test
    void unknown_route_returns_not_found_body_with_echoed_request_id() throws Exception {
        HttpResponse<String> r = send(request("/api/v1/does-not-exist").header("X-Request-Id", "req-12345678"));
        assertThat(r.statusCode()).isEqualTo(404);
        assertThat(r.body()).isEqualTo("{\"code\":\"NOT_FOUND\",\"messageKey\":\"error.not_found\",\"fieldErrors\":[],"
                + "\"requestId\":\"req-12345678\",\"retryable\":false}");
        assertThat(r.headers().firstValue("X-Request-Id")).hasValue("req-12345678");
    }

    @Test
    void malformed_inbound_request_id_is_replaced_not_reflected() throws Exception {
        HttpResponse<String> r = send(request("/api/v1/does-not-exist").header("X-Request-Id", "bad id <script>"));
        assertThat(r.headers().firstValue("X-Request-Id")).hasValueSatisfying(id -> assertThat(id).doesNotContain("script"));
        assertThat(r.body()).doesNotContain("script");
    }

    @Test
    void malformed_json_is_400() throws Exception {
        HttpResponse<String> r = send(postJson("/api/v1/test-probe", "{not json"));
        assertThat(r.statusCode()).isEqualTo(400);
        assertThat(r.body()).contains("\"code\":\"MALFORMED_REQUEST\"");
    }

    @Test
    void validation_failure_is_422_with_sorted_field_errors() throws Exception {
        HttpResponse<String> r = send(postJson("/api/v1/test-probe", "{\"name\":\"\",\"quantity\":0}"));
        assertThat(r.statusCode()).isEqualTo(422);
        assertThat(r.body()).contains("\"code\":\"VALIDATION_FAILED\"",
                "\"fieldErrors\":[{\"field\":\"name\",\"code\":\"NotBlank\"},{\"field\":\"quantity\",\"code\":\"Min\"}]");
    }

    @Test
    void wrong_media_type_and_method_are_reported() throws Exception {
        assertThat(send(request("/api/v1/test-probe").header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("x"))).statusCode()).isEqualTo(415);
        assertThat(send(request("/api/v1/test-probe").DELETE()).statusCode()).isEqualTo(405);
    }

    @Test
    void domain_conflict_maps_to_409_with_stable_code() throws Exception {
        HttpResponse<String> r = send(request("/api/v1/test-probe/conflict"));
        assertThat(r.statusCode()).isEqualTo(409);
        assertThat(r.body()).contains("\"code\":\"STOCK_CHANGED\"", "\"messageKey\":\"error.stock_changed\"")
                .doesNotContain("synthetic stock conflict");
    }

    @Test
    void unexpected_failure_is_500_without_internal_detail() throws Exception {
        HttpResponse<String> r = send(request("/api/v1/test-probe/boom"));
        assertThat(r.statusCode()).isEqualTo(500);
        assertThat(r.body()).contains("\"code\":\"INTERNAL_ERROR\"", "\"retryable\":true")
                .doesNotContain("SELECT", "IllegalStateException", "trace");
    }
}
