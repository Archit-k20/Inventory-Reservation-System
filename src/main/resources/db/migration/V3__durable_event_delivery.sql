CREATE TABLE event_outbox (
    id BIGSERIAL PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    topic VARCHAR(255) NOT NULL,
    message_key VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ,
    attempts INTEGER NOT NULL DEFAULT 0 CHECK (attempts >= 0),
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_error VARCHAR(1000)
);
CREATE INDEX idx_event_outbox_pending ON event_outbox (next_attempt_at, id)
    WHERE published_at IS NULL;

CREATE TABLE failed_kafka_records (
    id BIGSERIAL PRIMARY KEY,
    topic VARCHAR(255) NOT NULL,
    partition_id INTEGER NOT NULL,
    record_offset BIGINT NOT NULL,
    message_key TEXT,
    payload TEXT NOT NULL,
    headers TEXT NOT NULL,
    failure_reason VARCHAR(1000) NOT NULL,
    failed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (topic, partition_id, record_offset)
);
