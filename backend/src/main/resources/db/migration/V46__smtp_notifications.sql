CREATE TABLE IF NOT EXISTS smtp_settings (
  id BIGINT PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT FALSE,
  server_host VARCHAR(255) NOT NULL,
  server_port INTEGER NOT NULL,
  auth BOOLEAN NOT NULL DEFAULT FALSE,
  starttls BOOLEAN NOT NULL DEFAULT FALSE,
  ssl BOOLEAN NOT NULL DEFAULT FALSE,
  username VARCHAR(255),
  password VARCHAR(512),
  from_email VARCHAR(255) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

INSERT INTO smtp_settings (id, enabled, server_host, server_port, auth, starttls, ssl, username, password, from_email, updated_at)
VALUES (1, FALSE, 'localhost', 25, FALSE, FALSE, FALSE, NULL, NULL, 'no-reply@localhost', NOW())
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS notification_subscriptions (
  id BIGSERIAL PRIMARY KEY,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  notification_kind VARCHAR(32) NOT NULL,
  channel VARCHAR(16) NOT NULL,
  event_code VARCHAR(64) NOT NULL,
  recipient_email VARCHAR(255) NOT NULL,
  device_ip_filter VARCHAR(64),
  device_tag_filter VARCHAR(128),
  severity_filter VARCHAR(32),
  metric_filter VARCHAR(128),
  custom_condition VARCHAR(512),
  created_at TIMESTAMPTZ NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_notification_subscriptions_enabled_channel
  ON notification_subscriptions (enabled, channel);
