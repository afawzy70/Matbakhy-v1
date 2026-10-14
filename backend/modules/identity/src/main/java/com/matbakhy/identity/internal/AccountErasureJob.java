package com.matbakhy.identity.internal;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Runs due account erasures (MB-039); safe to run repeatedly and on several instances (row locks, idempotent erasers). */
@Component
@ConditionalOnProperty(name = "matbakhy.privacy.erasure-job.enabled", havingValue = "true", matchIfMissing = true)
class AccountErasureJob {

    private final AccountErasureService erasure;

    AccountErasureJob(AccountErasureService erasure) {
        this.erasure = erasure;
    }

    @Scheduled(fixedDelayString = "${matbakhy.privacy.erasure-job.interval:15m}")
    void run() {
        erasure.eraseDue();
    }
}
