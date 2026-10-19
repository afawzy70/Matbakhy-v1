package com.matbakhy.app.media;

import static org.assertj.core.api.Assertions.assertThat;

import com.matbakhy.app.support.IntegrationTest;
import com.matbakhy.catalog.api.ObjectStorage;
import com.matbakhy.catalog.api.ObjectStorage.Bucket;
import com.matbakhy.catalog.internal.media.MediaCleanup;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * CR2-001: durable storage cleanup with bounded retries, against PostgreSQL and the S3 emulator. A storage that
 * fails is simulated by a wrapper around the real storage; no product code is replaced.
 */
@IntegrationTest
class MediaCleanupIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectStorage storage;

    /** Wraps the real storage and fails the first {@code failures} deletes. */
    static final class FlakyStorage implements ObjectStorage {
        final ObjectStorage real;
        final AtomicInteger failures;

        FlakyStorage(ObjectStorage real, int failures) {
            this.real = real;
            this.failures = new AtomicInteger(failures);
        }

        @Override public URI presignUpload(String key, String contentType, long bytes, Duration ttl) { return real.presignUpload(key, contentType, bytes, ttl); }
        @Override public URI presignRead(String key, Duration ttl) { return real.presignRead(key, ttl); }
        @Override public OptionalLong size(Bucket bucket, String key) { return real.size(bucket, key); }
        @Override public byte[] read(Bucket bucket, String key, long maxBytes) { return real.read(bucket, key, maxBytes); }
        @Override public void write(Bucket bucket, String key, byte[] content, String contentType) { real.write(bucket, key, content, contentType); }
        @Override public URI publicUrl(String key) { return real.publicUrl(key); }

        @Override
        public void delete(Bucket bucket, String key) {
            if (failures.getAndUpdate(n -> n > 0 ? n - 1 : n) > 0) {
                throw new IllegalStateException("simulated storage outage");
            }
            real.delete(bucket, key);
        }
    }

    static final class MutableClock extends Clock {
        // A day ahead of the real clock: the scheduled cleanup job (real clock) never sees these rows as due (QA3-002).
        Instant now = Instant.now().plus(Duration.ofDays(1));

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private String state(String key) {
        return jdbc.queryForObject("SELECT state FROM media_cleanup WHERE object_key = ?", String.class, key);
    }

    private int attempts(String key) {
        return jdbc.queryForObject("SELECT attempts FROM media_cleanup WHERE object_key = ?", Integer.class, key);
    }

    private String orphan() {
        String key = "uploads/cleanup-" + UUID.randomUUID();
        storage.write(Bucket.PRIVATE, key, "synthetic bytes".getBytes(StandardCharsets.UTF_8), "application/pdf");
        return key;
    }

    @Test
    void a_storage_failure_is_retried_with_backoff_and_recovers_without_duplicates() {
        MutableClock clock = new MutableClock();
        MediaCleanup cleanup = new MediaCleanup(jdbc, new FlakyStorage(storage, 2), clock);
        String key = orphan();
        cleanup.enqueue(Bucket.PRIVATE, key, "TEST");
        cleanup.enqueue(Bucket.PRIVATE, key, "TEST");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_cleanup WHERE object_key = ?", Integer.class, key)).as("idempotent enqueue").isEqualTo(1);

        assertThat(cleanup.runDue()).isZero();
        assertThat(state(key)).isEqualTo("PENDING");
        assertThat(attempts(key)).isEqualTo(1);
        assertThat(storage.size(Bucket.PRIVATE, key)).as("still present after a failed delete").isPresent();

        assertThat(cleanup.runDue()).as("not due yet: back-off").isZero();
        assertThat(attempts(key)).isEqualTo(1);

        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertThat(cleanup.runDue()).isZero();
        assertThat(attempts(key)).isEqualTo(2);

        clock.now = clock.now.plus(Duration.ofMinutes(5));
        assertThat(cleanup.runDue()).isEqualTo(1);
        assertThat(state(key)).isEqualTo("DONE");
        assertThat(storage.size(Bucket.PRIVATE, key)).isEmpty();

        clock.now = clock.now.plus(Duration.ofHours(2));
        assertThat(cleanup.runDue()).as("a finished cleanup never runs again").isZero();
    }

    @Test
    void retries_are_bounded_and_the_exhausted_cleanup_stays_visible() {
        MutableClock clock = new MutableClock();
        MediaCleanup cleanup = new MediaCleanup(jdbc, new FlakyStorage(storage, Integer.MAX_VALUE), clock);
        String key = orphan();
        cleanup.enqueue(Bucket.PRIVATE, key, "TEST");
        for (int i = 0; i < 12; i++) {
            cleanup.runDue();
            clock.now = clock.now.plus(Duration.ofHours(2));
        }
        assertThat(state(key)).isEqualTo("FAILED");
        assertThat(attempts(key)).isEqualTo(6);
        assertThat(jdbc.queryForObject("SELECT last_error FROM media_cleanup WHERE object_key = ?", String.class, key)).isEqualTo("IllegalStateException");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM media_cleanup WHERE state = 'FAILED'", Integer.class)).isGreaterThanOrEqualTo(1);
        assertThat(storage.size(Bucket.PRIVATE, key)).as("failure is reported, bytes are not silently assumed gone").isPresent();
    }

    @Test
    void a_repeated_request_never_shortens_a_pending_deadline_and_rearms_a_finished_row() {
        MutableClock clock = new MutableClock();
        MediaCleanup cleanup = new MediaCleanup(jdbc, storage, clock);
        String key = orphan();
        cleanup.enqueue(Bucket.PRIVATE, key, "TEST", clock.now.plus(Duration.ofMinutes(10)));
        cleanup.enqueue(Bucket.PRIVATE, key, "TEST", clock.now); // earlier deadline must not win
        assertThat(cleanup.runDue()).as("deadline kept at +10 minutes").isZero();
        clock.now = clock.now.plus(Duration.ofMinutes(11));
        assertThat(cleanup.runDue()).isEqualTo(1);
        assertThat(state(key)).isEqualTo("DONE");

        storage.write(Bucket.PRIVATE, key, "again".getBytes(StandardCharsets.UTF_8), "application/pdf");
        cleanup.enqueue(Bucket.PRIVATE, key, "TEST");
        assertThat(state(key)).as("a finished row is re-armed by a new request").isEqualTo("PENDING");
        assertThat(cleanup.runDue()).isEqualTo(1);
        assertThat(storage.size(Bucket.PRIVATE, key)).isEmpty();
    }
}
