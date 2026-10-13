package com.matbakhy.catalog.web;

import com.matbakhy.catalog.internal.activity.ActivityService;
import com.matbakhy.catalog.internal.activity.ActivityService.Event;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API-110 (MB-054): organic activity from browsing; open to visitors, answers 202 with no body either way. */
@RestController
class ActivityController {

    record Body(String visitorId, List<Event> events) {
    }

    private final ActivityService activity;

    ActivityController(ActivityService activity) {
        this.activity = activity;
    }

    @PostMapping("/api/v1/activity")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void record(@RequestBody Body body, @RequestHeader(name = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        activity.record(body.visitorId(), body.events(), userAgent);
    }
}
