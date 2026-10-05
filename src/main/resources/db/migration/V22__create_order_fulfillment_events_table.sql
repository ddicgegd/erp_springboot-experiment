-- V22: Create order_fulfillment_events audit log table (append-only)
CREATE SEQUENCE order_fulfillment_events_seq START WITH 1 INCREMENT BY 50 NOCACHE NOCYCLE;

CREATE TABLE order_fulfillment_events (
    id NUMBER(19, 0) NOT NULL PRIMARY KEY,
    order_id NUMBER(19, 0) NOT NULL,
    order_number VARCHAR2(50) NOT NULL,
    status VARCHAR2(50) NOT NULL,
    stage_key VARCHAR2(100) NOT NULL,
    stage_title VARCHAR2(255) NOT NULL,
    diagnostic_message VARCHAR2(1000) NOT NULL,
    actor VARCHAR2(100),
    facility VARCHAR2(100),
    event_time TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE INDEX idx_fulfillment_events_order_id ON order_fulfillment_events(order_id);
CREATE INDEX idx_fulfillment_events_order_number ON order_fulfillment_events(order_number);
CREATE INDEX idx_fulfillment_events_event_time ON order_fulfillment_events(event_time);
