package com.matbakhy.orders.internal;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Expiry scheduler (BR-027): pending orders past their acceptance deadline become EXPIRED by server time and release
 * their stock once. Each order is handled in its own transaction through the lifecycle service; a concurrent accept
 * or cancel holding the row lock wins or loses cleanly. Disabled with matbakhy.orders.expiry-job.enabled=false.
 */
@Component
@ConditionalOnProperty(name = "matbakhy.orders.expiry-job.enabled", havingValue = "true", matchIfMissing = true)
public class OrderExpiryJob {

    private static final Logger LOG = LoggerFactory.getLogger(OrderExpiryJob.class);

    private final OrderLifecycleService lifecycle;

    public OrderExpiryJob(OrderLifecycleService lifecycle) {
        this.lifecycle = lifecycle;
    }

    @Scheduled(fixedDelayString = "${matbakhy.orders.expiry-job.interval:30s}")
    public void run() {
        int expired = expireDue();
        if (expired > 0) {
            LOG.info("expired {} pending orders", expired);
        }
    }

    public int expireDue() {
        int expired = 0;
        for (UUID id : lifecycle.dueForExpiry(200)) {
            if (lifecycle.expireOne(id)) {
                expired++;
            }
        }
        return expired;
    }
}
