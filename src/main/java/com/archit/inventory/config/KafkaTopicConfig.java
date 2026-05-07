package com.archit.inventory.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String RESERVATION_CREATED_TOPIC = "inventory.reservation.created";
    public static final String RESERVATION_CONFIRMED_TOPIC = "inventory.reservation.confirmed";
    public static final String OUT_OF_STOCK_TOPIC = "inventory.out-of-stock";
    public static final String ORDER_FAILED_TOPIC = "order.failed";

    @Bean
    public NewTopic reservationCreatedTopic() {
        return TopicBuilder.name(RESERVATION_CREATED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic reservationConfirmedTopic() {
        return TopicBuilder.name(RESERVATION_CONFIRMED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic outOfStockTopic() {
        return TopicBuilder.name(OUT_OF_STOCK_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic orderFailedTopic() {
        return TopicBuilder.name(ORDER_FAILED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
