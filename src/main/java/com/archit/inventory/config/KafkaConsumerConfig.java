package com.archit.inventory.config;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Configuration
public class KafkaConsumerConfig {
    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    public DefaultErrorHandler kafkaErrorHandler() {
        return new DefaultErrorHandler(this::logFailedRecord, new FixedBackOff(1000L, 3L));
    }

    private void logFailedRecord(ConsumerRecord<?, ?> record, Exception exception) {
        log.error("Kafka record processing failed after retries",
                kv("topic", record.topic()),
                kv("partition", record.partition()),
                kv("offset", record.offset()),
                kv("error", exception.getMessage()));
    }
}
