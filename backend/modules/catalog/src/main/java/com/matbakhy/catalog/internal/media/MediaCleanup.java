package com.matbakhy.catalog.internal.media;

import com.matbakhy.catalog.api.ObjectStorage;
import com.matbakhy.catalog.api.ObjectStorage.Bucket;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Durable storage cleanup (CR2-001). {@link #enqueue} runs inside the transaction that unreferences an object, so the
 * intent commits or rolls back with it. {@link #runDue} deletes with bounded retries and exponential back-off; it never
 * deletes an object an active asset still references, and is safe to run repeatedly and on several instances.
 */
@Component
public class MediaCleanup {

    static final int MAX_ATTEMPTS = 6;
    static final int BATCH = 20;
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(30);
    private static final Duration MAX_BACKOFF = Duration.ofHours(1);
    private static final Logger log = LoggerFactory.getLogger(MediaCleanup.class);

    private final JdbcTemplate jdbc;
    private final ObjectStorage storage;
    private final Clock clock;

    public MediaCleanup(JdbcTemplate jdbc, ObjectStorage storage, Clock clock) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.clock = clock;
    }

    /** Idempotent: a second request for the same object is a no-op. */
    @Transactional
    public void enqueue(Bucket bucket, String key, String reason) {
        enqueue(bucket, key, reason, clock.instant());
    }

    /** As above, but the object is not touched before {@code notBefore} (e.g. until an outstanding upload ticket has expired). */
    @Transactional
    public void enqueue(Bucket bucket, String key, String reason, Instant notBefore) {
        Timestamp now = Timestamp.from(clock.instant());
        Instant due = notBefore.isAfter(clock.instant()) ? notBefore : clock.instant();
        jdbc.update("""
                INSERT INTO media_cleanup (id, bucket, object_key, reason, next_attempt_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?) ON CONFLICT (bucket, object_key) DO NOTHING""",
                UUID.randomUUID(), bucket.name(), key, reason, Timestamp.from(due), now, now);
    }

    /** Processes due rows once; returns how many objects were deleted. */
    @Transactional
    public int runDue() {
        Instant now = clock.instant();
        List<Map<String, Object>> due = jdbc.queryForList("""
                SELECT id, bucket, object_key, attempts FROM media_cleanup
                WHERE state = 'PENDING' AND next_attempt_at <= ? ORDER BY next_attempt_at LIMIT ? FOR UPDATE SKIP LOCKED""",
                Timestamp.from(now), BATCH);
        int deleted = 0;
        for (Map<String, Object> row : due) {
            UUID id = (UUID) row.get("id");
            String key = (String) row.get("object_key");
            Bucket bucket = Bucket.valueOf((String) row.get("bucket"));
            int attempts = ((Number) row.get("attempts")).intValue();
            Boolean referenced = jdbc.queryForObject("""
                    SELECT EXISTS (SELECT 1 FROM media_asset WHERE state IN ('PENDING_UPLOAD', 'QUARANTINED', 'APPROVED')
                                   AND (private_key = ? OR public_key = ?))""", Boolean.class, key, key);
            if (Boolean.TRUE.equals(referenced)) {
                finish(id, "SKIPPED_REFERENCED", attempts, null, now);
                log.warn("media cleanup skipped: object still referenced by an active asset (cleanup {})", id);
                continue;
            }
            try {
                storage.delete(bucket, key);
                finish(id, "DONE", attempts + 1, null, now);
                deleted++;
            } catch (RuntimeException e) {
                int next = attempts + 1;
                String error = e.getClass().getSimpleName();
                if (next >= MAX_ATTEMPTS) {
                    finish(id, "FAILED", next, error, now);
                    log.error("media cleanup gave up after {} attempts (cleanup {}, {})", next, id, error);
                } else {
                    Duration wait = BASE_BACKOFF.multipliedBy(1L << Math.min(next, 10));
                    if (wait.compareTo(MAX_BACKOFF) > 0) {
                        wait = MAX_BACKOFF;
                    }
                    jdbc.update("UPDATE media_cleanup SET attempts = ?, last_error = ?, next_attempt_at = ?, updated_at = ? WHERE id = ?",
                            next, error, Timestamp.from(now.plus(wait)), Timestamp.from(now), id);
                    log.warn("media cleanup attempt {} failed, retrying in {} (cleanup {}, {})", next, wait, id, error);
                }
            }
        }
        return deleted;
    }

    private void finish(UUID id, String state, int attempts, String error, Instant now) {
        jdbc.update("UPDATE media_cleanup SET state = ?, attempts = ?, last_error = ?, updated_at = ? WHERE id = ?",
                state, attempts, error, Timestamp.from(now), id);
    }
}
