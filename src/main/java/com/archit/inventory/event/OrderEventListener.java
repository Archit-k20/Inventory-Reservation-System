package com.archit.inventory.event;

import com.archit.inventory.config.KafkaTopicConfig;
import com.archit.inventory.reservation.ReservationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
public class OrderEventListener {
    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private final ReservationService reservationService;

    public OrderEventListener(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @KafkaListener(topics = KafkaTopicConfig.ORDER_FAILED_TOPIC)
    public void handleOrderFailed(OrderFailedEvent event) {
        log.info("Received OrderFailedEvent",
                kv("reservationId", event.reservationId()),
                kv("reason", event.reason()));
        reservationService.releaseAfterOrderFailed(event.reservationId());
        log.info("Processed OrderFailedEvent",
                kv("reservationId", event.reservationId()));
    }
}
