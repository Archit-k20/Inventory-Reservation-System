package com.archit.inventory.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OutboxRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public OutboxRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public UUID enqueue(String topic, String key, Object event) {
        UUID eventId = UUID.randomUUID();
        try {
            jdbc.update("""
                    INSERT INTO event_outbox (event_id, topic, message_key, event_type, payload)
                    VALUES (?, ?, ?, ?, ?)
                    """, eventId, topic, key, event.getClass().getSimpleName(), json.writeValueAsString(event));
            return eventId;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Unable to serialize inventory event", exception);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<PendingEvent> lockNext() {
        return jdbc.query("""
                SELECT id, event_id, topic, message_key, event_type, payload, attempts
                FROM event_outbox
                WHERE published_at IS NULL AND next_attempt_at <= now()
                ORDER BY next_attempt_at, id
                LIMIT 1 FOR UPDATE SKIP LOCKED
                """, (row, number) -> new PendingEvent(row.getLong("id"),
                row.getObject("event_id", UUID.class), row.getString("topic"),
                row.getString("message_key"), row.getString("event_type"),
                row.getString("payload"), row.getInt("attempts"))).stream().findFirst();
    }

    public void published(long id) {
        jdbc.update("UPDATE event_outbox SET published_at = now(), attempts = attempts + 1, last_error = NULL WHERE id = ?", id);
    }

    public void retry(PendingEvent event, Exception exception) {
        long delaySeconds = Math.min(60, 1L << Math.min(event.attempts(), 6));
        String reason = exception.getClass().getSimpleName() + ": " + exception.getMessage();
        jdbc.update("""
                UPDATE event_outbox SET attempts = attempts + 1, last_error = ?,
                next_attempt_at = now() + (? * interval '1 second') WHERE id = ?
                """, reason.substring(0, Math.min(1000, reason.length())), delaySeconds, event.id());
    }

    public record PendingEvent(long id, UUID eventId, String topic, String key,
                               String type, String payload, int attempts) { }
}
