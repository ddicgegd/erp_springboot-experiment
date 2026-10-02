-- V20__create_user_subscriptions_tables.sql
-- Module: Google Play In-App Subscriptions (Plans: 3M, 6M, 12M with 1 Month Free Trial)

CREATE TABLE subscription_plans (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    plan_code VARCHAR2(50) NOT NULL UNIQUE,
    google_product_id VARCHAR2(100) NOT NULL,
    base_plan_id VARCHAR2(50) NOT NULL,
    target_rank VARCHAR2(20) NOT NULL,
    billing_period_months NUMBER(2) NOT NULL,
    free_trial_days NUMBER(3) DEFAULT 30 NOT NULL,
    price_amount NUMBER(15, 2) NOT NULL,
    currency VARCHAR2(10) DEFAULT 'VND' NOT NULL,
    is_active NUMBER(1) DEFAULT 1 NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
);

CREATE TABLE user_subscriptions (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id NUMBER NOT NULL,
    plan_id NUMBER NOT NULL,
    purchase_token VARCHAR2(500) NOT NULL UNIQUE,
    order_id VARCHAR2(100),
    subscription_state VARCHAR2(30) NOT NULL,
    is_trial NUMBER(1) DEFAULT 1 NOT NULL,
    start_time TIMESTAMP NOT NULL,
    expiry_time TIMESTAMP NOT NULL,
    auto_renewing NUMBER(1) DEFAULT 1 NOT NULL,
    linked_purchase_token VARCHAR2(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_user_sub_user FOREIGN KEY (user_id) REFERENCES Users(id),
    CONSTRAINT fk_user_sub_plan FOREIGN KEY (plan_id) REFERENCES subscription_plans(id)
);

CREATE INDEX idx_user_sub_user_id ON user_subscriptions(user_id);
CREATE INDEX idx_user_sub_token ON user_subscriptions(purchase_token);
CREATE INDEX idx_user_sub_state ON user_subscriptions(subscription_state);

CREATE TABLE subscription_audit_logs (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    subscription_id NUMBER,
    purchase_token VARCHAR2(500),
    notification_type NUMBER(2) NOT NULL,
    raw_payload CLOB,
    processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT fk_sub_audit_sub FOREIGN KEY (subscription_id) REFERENCES user_subscriptions(id) ON DELETE SET NULL
);

CREATE INDEX idx_sub_audit_sub_id ON subscription_audit_logs(subscription_id);
CREATE INDEX idx_sub_audit_token ON subscription_audit_logs(purchase_token);
