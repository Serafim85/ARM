ALTER TABLE monitoring_telemetry_snapshot
  ALTER COLUMN cpu_current DROP NOT NULL,
  ALTER COLUMN cpu_average DROP NOT NULL,
  ALTER COLUMN cpu_peak DROP NOT NULL,
  ALTER COLUMN ram_used_percent DROP NOT NULL,
  ALTER COLUMN rom_used_percent DROP NOT NULL;
