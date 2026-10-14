package com.matbakhy.catalog.web;

import com.matbakhy.catalog.internal.discovery.DiscoveryService;
import com.matbakhy.catalog.internal.discovery.DiscoveryService.MealPage;
import com.matbakhy.catalog.internal.discovery.DiscoveryService.Query;
import com.matbakhy.catalog.internal.discovery.DiscoveryService.Sort;
import com.matbakhy.catalog.internal.discovery.ZoneService;
import com.matbakhy.catalog.internal.discovery.ZoneService.ZonePage;
import com.matbakhy.catalog.internal.meal.MealService.Category;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API-002 (service zones) and API-003 (meal discovery). Public; Arabic by default, English on request. */
@RestController
class DiscoveryController {

    private final DiscoveryService discovery;
    private final ZoneService zones;

    DiscoveryController(DiscoveryService discovery, ZoneService zones) {
        this.discovery = discovery;
        this.zones = zones;
    }

    @GetMapping("/api/v1/meals")
    ResponseEntity<MealPage> meals(@RequestParam(required = false) String q, @RequestParam(required = false) UUID zoneId,
                                   @RequestParam(required = false) LocalDate date, @RequestParam(required = false) UUID slotId,
                                   @RequestParam(required = false) Category categoryId, @RequestParam(required = false) Long minPriceMinor,
                                   @RequestParam(required = false) Long maxPriceMinor, @RequestParam(required = false) UUID kitchenId,
                                   @RequestParam(required = false) Sort sort, @RequestParam(required = false) String cursor,
                                   @RequestParam(defaultValue = "20") int limit,
                                   @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        MealPage page = discovery.search(new Query(q, zoneId, date, slotId, categoryId, minPriceMinor, maxPriceMinor, kitchenId, sort, cursor,
                limit, "en".equals(MealController.preferredLanguage(acceptLanguage))));
        return ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE).body(page);
    }

    @GetMapping("/api/v1/zones")
    ResponseEntity<ZonePage> zones(@RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int limit,
                                   @RequestHeader(name = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        ZonePage page = zones.activeZones(cursor, limit, "en".equals(MealController.preferredLanguage(acceptLanguage)));
        return ResponseEntity.ok().header(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE).body(page);
    }
}
