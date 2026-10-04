ALTER TABLE USERS ADD (
    google_id VARCHAR2(100),
    auth_provider VARCHAR2(30) DEFAULT 'LOCAL'
);

CREATE UNIQUE INDEX idx_users_google_id ON USERS (google_id);
