package com.matbakhy.app.support;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Test clock that follows real time plus an adjustable offset (deterministic expiry checks), or stands still at a
 * fixed instant for exact boundary checks.
 */
public final class MutableClock extends Clock {

    private volatile Duration offset = Duration.ZERO;
    private volatile Instant frozen;

    public void advance(Duration by) {
        offset = offset.plus(by);
    }

    /** Stops the clock at {@code at} until {@link #reset()}. */
    public void freezeAt(Instant at) {
        frozen = at;
    }

    public void reset() {
        offset = Duration.ZERO;
        frozen = null;
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        return this;
    }

    @Override
    public Instant instant() {
        Instant f = frozen;
        return f != null ? f : Instant.now().plus(offset);
    }
}
