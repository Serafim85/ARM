-- Single index aligned with trigger/history reads: device + metric_name + instance + time.

CREATE INDEX IF NOT EXISTS idx_metric_values_device_metric_instance_time
  ON metric_values (device_ip, metric_name, instance_key, recorded_at DESC);

DROP INDEX IF EXISTS idx_metric_values_device_metric_time;
DROP INDEX IF EXISTS idx_metric_values_item_instance_time;
DROP INDEX IF EXISTS idx_metric_values_device_time_non_null;
