package com.archit.inventory.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
public class FailedKafkaRecordStore {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public FailedKafkaRecordStore(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional
    public void save(ConsumerRecord<?, ?> record, Exception exception) {
        List<SavedHeader> headers = new ArrayList<>();
        record.headers().forEach(header -> headers.add(new SavedHeader(header.key(),
                header.value() == null ? null : Base64.getEncoder().encodeToString(header.value()))));
        String reason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        try {
            jdbc.update("""
                    INSERT INTO failed_kafka_records
                    (topic, partition_id, record_offset, message_key, payload, headers, failure_reason)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON CONFLICT (topic, partition_id, record_offset) DO NOTHING
                    """, record.topic(), record.partition(), record.offset(),
                    record.key() == null ? null : record.key().toString(),
                    json.writeValueAsString(record.value()), json.writeValueAsString(headers),
                    reason.substring(0, Math.min(1000, reason.length())));
        } catch (JsonProcessingException serializationFailure) {
            throw new IllegalStateException("Unable to preserve failed Kafka record", serializationFailure);
        }
        // Database failures propagate so Kafka retries instead of losing the record.
    }

    private record SavedHeader(String name, String base64Value) { }
}
