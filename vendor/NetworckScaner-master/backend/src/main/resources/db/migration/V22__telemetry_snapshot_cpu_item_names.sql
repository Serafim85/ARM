ALTER TABLE monitoring_telemetry_snapshot
  ADD COLUMN IF NOT EXISTS cpu_current_item_name VARCHAR(512),
  ADD COLUMN IF NOT EXISTS cpu_average_item_name VARCHAR(512),
  ADD COLUMN IF NOT EXISTS cpu_peak_item_name VARCHAR(512);
