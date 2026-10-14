package com.matbakhy.notifications.internal;

import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Scheduled dispatcher and delivery worker; each instance uses its own lease owner id. */
@Component
@ConditionalOnProperty(name = "matbakhy.notifications.jobs-enabled", havingValue = "true", matchIfMissing = true)
class NotificationJobs {

    private final String worker = "worker-" + UUID.randomUUID();
    private final OutboxDispatcher dispatcher;
    private final DeliveryWorker delivery;

    NotificationJobs(OutboxDispatcher dispatcher, DeliveryWorker delivery) {
        this.dispatcher = dispatcher;
        this.delivery = delivery;
    }

    @Scheduled(fixedDelayString = "${matbakhy.notifications.poll-interval:5s}")
    void run() {
        dispatcher.dispatchOnce(worker, 100);
        delivery.deliverOnce(worker, 100);
    }
}
