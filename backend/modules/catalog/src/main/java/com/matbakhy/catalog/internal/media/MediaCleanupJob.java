package com.matbakhy.catalog.internal.media;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs due storage cleanups (CR2-001); idempotent and safe on several instances. */
@Component
@ConditionalOnProperty(name = "matbakhy.media.cleanup-job.enabled", havingValue = "true", matchIfMissing = true)
class MediaCleanupJob {

    private final MediaCleanup cleanup;

    MediaCleanupJob(MediaCleanup cleanup) {
        this.cleanup = cleanup;
    }

    @Scheduled(fixedDelayString = "${matbakhy.media.cleanup-job.interval:30s}")
    void run() {
        cleanup.runDue();
    }
}
