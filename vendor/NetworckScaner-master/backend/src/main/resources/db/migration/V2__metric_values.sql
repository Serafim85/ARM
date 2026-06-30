CREATE TABLE IF NOT EXISTS metric_values (
  recorded_at  TIMESTAMPTZ      NOT NULL,
  device_ip    VARCHAR(64)      NOT NULL,
  metric_name  VARCHAR(128)     NOT NULL,
  metric_value DOUBLE PRECISION NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_metric_values_device_metric_time
  ON metric_values(device_ip, metric_name, recorded_at DESC);

SELECT create_hypertable('metric_values', 'recorded_at', if_not_exists => TRUE);
