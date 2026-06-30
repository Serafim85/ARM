-- TimescaleDB policies for metric_values: chunking, compression, retention, 1m continuous aggregate.

SELECT set_chunk_time_interval('metric_values', INTERVAL '1 day');

ALTER TABLE metric_values SET (
  timescaledb.compress,
  timescaledb.compress_segmentby = 'device_ip, metric_name',
  timescaledb.compress_orderby = 'recorded_at DESC'
);

SELECT add_compression_policy(
  'metric_values',
  compress_after => INTERVAL '2 days',
  schedule_interval => INTERVAL '12 hours',
  if_not_exists => TRUE
);

SELECT add_retention_policy(
  'metric_values',
  drop_after => INTERVAL '10 days',
  schedule_interval => INTERVAL '6 hours',
  if_not_exists => TRUE
);

CREATE MATERIALIZED VIEW metric_values_1m
WITH (timescaledb.continuous) AS
SELECT
  time_bucket('1 minute', recorded_at) AS bucket,
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
  'metric_values_1m',
  start_offset => INTERVAL '3 hours',
  end_offset => INTERVAL '1 minute',
  schedule_interval => INTERVAL '1 hour',
  if_not_exists => TRUE
);

SELECT add_retention_policy(
  'metric_values_1m',
  drop_after => INTERVAL '90 days',
  schedule_interval => INTERVAL '1 day',
  if_not_exists => TRUE
);

CREATE INDEX IF NOT EXISTS idx_metric_values_1m_device_metric_bucket
  ON metric_values_1m (device_ip, metric_name, bucket DESC);
