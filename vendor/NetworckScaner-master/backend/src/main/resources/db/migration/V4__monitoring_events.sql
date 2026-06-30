CREATE TABLE IF NOT EXISTS monitoring_events (
  id                 BIGSERIAL      NOT NULL,
  device_id          BIGINT         NOT NULL REFERENCES monitored_devices (id) ON DELETE CASCADE,
  template_id        VARCHAR(128),
  metric_name        VARCHAR(128)   NOT NULL,
  threshold_level    VARCHAR(32)    NOT NULL,
  threshold_value    DOUBLE PRECISION NOT NULL,
  actual_value       DOUBLE PRECISION NOT NULL,
  breach_started_at  TIMESTAMPTZ    NOT NULL,
  normalized_at      TIMESTAMPTZ,
  status             VARCHAR(32)    NOT NULL,
  PRIMARY KEY (breach_started_at, id)
);

CREATE INDEX IF NOT EXISTS idx_monitoring_events_device_status
  ON monitoring_events (device_id, status);

CREATE INDEX IF NOT EXISTS idx_monitoring_events_device_breach_started
  ON monitoring_events (device_id, breach_started_at DESC);

SELECT create_hypertable(
  'monitoring_events',
  'breach_started_at',
  chunk_time_interval => INTERVAL '7 days',
  if_not_exists => TRUE
);
