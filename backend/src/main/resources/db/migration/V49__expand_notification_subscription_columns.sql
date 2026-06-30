ALTER TABLE notification_subscriptions
  ALTER COLUMN event_code TYPE VARCHAR(512),
  ALTER COLUMN recipient_email TYPE VARCHAR(1024),
  ALTER COLUMN device_ip_filter TYPE VARCHAR(2048),
  ALTER COLUMN device_tag_filter TYPE VARCHAR(512),
  ALTER COLUMN severity_filter TYPE VARCHAR(128),
  ALTER COLUMN metric_filter TYPE VARCHAR(2048),
  ALTER COLUMN custom_condition TYPE VARCHAR(2048);
