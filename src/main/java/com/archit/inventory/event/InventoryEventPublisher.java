package com.archit.inventory.event;

import com.archit.inventory.config.KafkaTopicConfig;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
@Transactional
public class InventoryEventPublisher {
    private final OutboxRepository outbox;

    public InventoryEventPublisher(OutboxRepository outbox) {
        this.outbox = outbox;
    }

    public void publishReservationCreated(ReservationCreatedEvent event) {
        outbox.enqueue(KafkaTopicConfig.RESERVATION_CREATED_TOPIC, event.productId().toString(), event);
    }

    public void publishReservationConfirmed(ReservationConfirmedEvent event) {
        outbox.enqueue(KafkaTopicConfig.RESERVATION_CONFIRMED_TOPIC, event.productId().toString(), event);
    }

    public void publishOutOfStock(InventoryOutOfStockEvent event) {
        outbox.enqueue(KafkaTopicConfig.OUT_OF_STOCK_TOPIC, event.productId().toString(), event);
    }

    public UUID publishOrderFailed(OrderFailedEvent event) {
        return outbox.enqueue(KafkaTopicConfig.ORDER_FAILED_TOPIC, event.reservationId().toString(), event);
    }
}
