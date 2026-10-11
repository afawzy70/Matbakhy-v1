package com.matbakhy.app.web;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.IntegrationTest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The endpoint registry's implemented flags match the API routes the application actually serves. */
@IntegrationTest
class ContractRoutesTest {

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    RequestMappingHandlerMapping mappings;

    @Autowired
    JsonMapper json;

    private static String normalize(String path) {
        return path.replaceAll("\\{[^}]+}", "{}");
    }

    @Test
    void implemented_registry_entries_and_served_api_routes_are_the_same_set() throws Exception {
        JsonNode registry = json.readTree(Files.readString(Path.of("../../contracts/endpoints.json")));
        Set<String> implemented = new TreeSet<>();
        for (JsonNode e : registry.get("endpoints")) {
            if (e.get("implemented").asBoolean()) {
                implemented.add(e.get("method").asString() + " " + normalize(e.get("path").asString()));
            }
        }
        Set<String> served = new TreeSet<>();
        mappings.getHandlerMethods().forEach((info, handler) -> {
            if (handler.getBeanType().getName().contains("Test")) {
                return; // probe controllers declared by tests
            }
            for (String pattern : info.getPatternValues()) {
                if (pattern.startsWith("/api/")) {
                    info.getMethodsCondition().getMethods().forEach(m -> served.add(m.name() + " " + normalize(pattern)));
                }
            }
        });
        assertThat(served).isEqualTo(implemented);
    }
}
