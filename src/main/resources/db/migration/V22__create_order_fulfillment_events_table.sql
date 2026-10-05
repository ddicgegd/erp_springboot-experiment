-- V22: Create order_fulfillment_events audit log table (append-only)
CREATE TABLE order_fulfillment_events (
    id BIGSERIAL PRIMARY KEY,
    order_id BIGINT NOT NULL,
    order_number VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL,
    stage_key VARCHAR(100) NOT NULL,
    stage_title VARCHAR(255) NOT NULL,
    diagnostic_message VARCHAR(1000) NOT NULL,
    actor VARCHAR(100),
    facility VARCHAR(100),
    event_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_fulfillment_events_order_id ON order_fulfillment_events(order_id);
CREATE INDEX idx_fulfillment_events_order_number ON order_fulfillment_events(order_number);
CREATE INDEX idx_fulfillment_events_event_time ON order_fulfillment_events(event_time);
