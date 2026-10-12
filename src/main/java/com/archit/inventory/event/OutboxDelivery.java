package com.archit.inventory.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxDelivery {
    private static final Logger log = LoggerFactory.getLogger(OutboxDelivery.class);
    private final OutboxRepository outbox;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper json;
    private final Duration ackTimeout;

    public OutboxDelivery(OutboxRepository outbox, KafkaTemplate<String, Object> kafka,
                          ObjectMapper json, @Value("${inventory.outbox.ack-timeout:PT6S}") Duration ackTimeout) {
        this.outbox = outbox;
        this.kafka = kafka;
        this.json = json;
        this.ackTimeout = ackTimeout;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean publishNext() {
        var pending = outbox.lockNext();
        if (pending.isEmpty()) {
            return false;
        }
        var event = pending.get();
        try {
            var record = new ProducerRecord<String, Object>(event.topic(), event.key(), decode(event));
            record.headers().add("inventory-event-id", event.eventId().toString().getBytes(StandardCharsets.UTF_8));
            kafka.send(record).get(ackTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (Exception exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            outbox.retry(event, exception);
            log.warn("Event {} remains queued after delivery failure: {}", event.eventId(), exception.toString());
            return false;
        }
        // A crash after acknowledgement can redeliver the same event. Consumers
        // must be idempotent; the stable event ID is retained across retries.
        outbox.published(event.id());
        log.info("Kafka acknowledged event {} on {}", event.eventId(), event.topic());
        return true;
    }

    private Object decode(OutboxRepository.PendingEvent event) throws JsonProcessingException {
        return switch (event.type()) {
            case "ReservationCreatedEvent" -> json.readValue(event.payload(), ReservationCreatedEvent.class);
            case "ReservationConfirmedEvent" -> json.readValue(event.payload(), ReservationConfirmedEvent.class);
            case "InventoryOutOfStockEvent" -> json.readValue(event.payload(), InventoryOutOfStockEvent.class);
            case "OrderFailedEvent" -> json.readValue(event.payload(), OrderFailedEvent.class);
            default -> throw new IllegalArgumentException("Unsupported event type: " + event.type());
        };
    }
}
