CREATE TABLE app_audit_event (
    id BIGSERIAL PRIMARY KEY,
    occurred_at TIMESTAMPTZ NOT NULL,
    actor_login VARCHAR(320) NOT NULL,
    category VARCHAR(64) NOT NULL,
    action VARCHAR(32) NOT NULL,
    target VARCHAR(512) NOT NULL,
    details TEXT
);

CREATE INDEX idx_app_audit_event_occurred_at ON app_audit_event (occurred_at DESC);
