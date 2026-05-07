package com.archit.inventory.event;

import com.archit.inventory.config.KafkaTopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
public class InventoryEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(InventoryEventPublisher.class);
    private final KafkaTemplate<String, Object> kafkaTemplate;

    public InventoryEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishReservationCreated(ReservationCreatedEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.RESERVATION_CREATED_TOPIC, event.productId().toString(), event);
        log.info("Published ReservationCreatedEvent",
                kv("topic", KafkaTopicConfig.RESERVATION_CREATED_TOPIC),
                kv("reservationId", event.reservationId()),
                kv("productId", event.productId()));
    }

    public void publishReservationConfirmed(ReservationConfirmedEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.RESERVATION_CONFIRMED_TOPIC, event.productId().toString(), event);
        log.info("Published ReservationConfirmedEvent",
                kv("topic", KafkaTopicConfig.RESERVATION_CONFIRMED_TOPIC),
                kv("reservationId", event.reservationId()),
                kv("orderId", event.orderId()));
    }

    public void publishOutOfStock(InventoryOutOfStockEvent event) {
        kafkaTemplate.send(KafkaTopicConfig.OUT_OF_STOCK_TOPIC, event.productId().toString(), event);
        log.warn("Published InventoryOutOfStockEvent",
                kv("topic", KafkaTopicConfig.OUT_OF_STOCK_TOPIC),
                kv("productId", event.productId()),
                kv("sku", event.sku()));
    }
}
