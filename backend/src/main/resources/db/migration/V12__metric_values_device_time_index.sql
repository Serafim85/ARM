CREATE INDEX IF NOT EXISTS idx_metric_values_device_time_non_null
  ON metric_values(device_ip, recorded_at DESC)
  WHERE metric_value IS NOT NULL;
