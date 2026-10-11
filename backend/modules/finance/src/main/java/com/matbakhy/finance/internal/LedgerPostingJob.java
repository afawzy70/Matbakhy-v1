package com.matbakhy.finance.internal;

import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Posts commission for completion events; safe to run repeatedly and concurrently (unique source event). */
@Component
@ConditionalOnProperty(name = "matbakhy.finance.posting-job.enabled", havingValue = "true", matchIfMissing = true)
public class LedgerPostingJob {

    private final LedgerService ledger;

    public LedgerPostingJob(LedgerService ledger) {
        this.ledger = ledger;
    }

    @Scheduled(fixedDelayString = "${matbakhy.finance.posting-job.interval:30s}")
    public void run() {
        postPending();
    }

    public int postPending() {
        int posted = 0;
        for (Map<String, Object> row : ledger.unpostedCompletions(200)) {
            if (ledger.postCommission((UUID) row.get("event_id"), (UUID) row.get("order_id"))) {
                posted++;
            }
        }
        return posted;
    }
}
