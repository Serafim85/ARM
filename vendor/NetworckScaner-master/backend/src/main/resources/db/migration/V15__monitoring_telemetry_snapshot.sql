CREATE TABLE IF NOT EXISTS monitoring_telemetry_snapshot (
  id BIGSERIAL PRIMARY KEY,
  device_id BIGINT NOT NULL REFERENCES monitored_devices(id) ON DELETE CASCADE,
  cpu_current INTEGER NOT NULL,
  cpu_average INTEGER NOT NULL,
  cpu_peak INTEGER NOT NULL,
  ram_used_percent INTEGER NOT NULL,
  rom_used_percent INTEGER NOT NULL,
  uptime VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  admin_contact VARCHAR(255) NOT NULL,
  hardware_version VARCHAR(255) NOT NULL,
  location VARCHAR(255) NOT NULL,
  added_at VARCHAR(255) NOT NULL,
  boot_version VARCHAR(255) NOT NULL,
  collected_at TIMESTAMPTZ NOT NULL,
  source VARCHAR(64) NOT NULL,
  live_mode BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT uq_monitoring_telemetry_snapshot_device UNIQUE (device_id)
);

CREATE INDEX IF NOT EXISTS idx_monitoring_telemetry_snapshot_collected_at
  ON monitoring_telemetry_snapshot(collected_at DESC);
