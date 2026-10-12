package com.archit.inventory.event;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "inventory.outbox.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxScheduler {
    private final OutboxDelivery delivery;
    private final int batchSize;

    public OutboxScheduler(OutboxDelivery delivery, @Value("${inventory.outbox.batch-size:25}") int batchSize) {
        this.delivery = delivery;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${inventory.outbox.poll-ms:1000}")
    public void publishPending() {
        for (int i = 0; i < batchSize && delivery.publishNext(); i++) {
            // Each event is locked and acknowledged in its own transaction.
        }
    }
}
