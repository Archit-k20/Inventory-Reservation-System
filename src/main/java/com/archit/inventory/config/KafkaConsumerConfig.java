package com.archit.inventory.config;

import com.archit.inventory.event.FailedKafkaRecordStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaConsumerConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(FailedKafkaRecordStore failures) {
        return new DefaultErrorHandler((record, exception) -> {
            failures.save(record, exception);
            log.error("Preserved failed Kafka record {}-{}@{} for recovery",
                    record.topic(), record.partition(), record.offset());
        }, new FixedBackOff(1000L, 3L));
    }
}
