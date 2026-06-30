-- Hourly continuous aggregate for chart history (7–90 days); retire 1m aggregate.

SELECT remove_retention_policy('metric_values_1m', if_exists => TRUE);
SELECT remove_continuous_aggregate_policy('metric_values_1m', if_exists => TRUE);
DROP MATERIALIZED VIEW IF EXISTS metric_values_1m CASCADE;

CREATE MATERIALIZED VIEW metric_values_1h
WITH (timescaledb.continuous) AS
SELECT
  time_bucket('1 hour', recorded_at) AS bucket,
  device_ip,
  metric_name,
  instance_key,
  AVG(metric_value) AS avg_value,
  MIN(metric_value) AS min_value,
  MAX(metric_value) AS max_value,
  COUNT(*) AS sample_count
FROM metric_values
WHERE metric_value IS NOT NULL
GROUP BY bucket, device_ip, metric_name, instance_key
WITH NO DATA;

SELECT add_continuous_aggregate_policy(
  'metric_values_1h',
  start_offset => INTERVAL '3 hours',
  end_offset => INTERVAL '1 hour',
  schedule_interval => INTERVAL '1 hour',
  if_not_exists => TRUE
);

SELECT add_retention_policy(
  'metric_values_1h',
  drop_after => INTERVAL '90 days',
  schedule_interval => INTERVAL '1 day',
  if_not_exists => TRUE
);

CREATE INDEX IF NOT EXISTS idx_metric_values_1h_device_metric_bucket
  ON metric_values_1h (device_ip, metric_name, bucket DESC);
