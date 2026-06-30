ALTER TABLE monitoring_telemetry_snapshot
  ALTER COLUMN cpu_current TYPE DOUBLE PRECISION USING cpu_current::double precision,
  ALTER COLUMN cpu_average TYPE DOUBLE PRECISION USING cpu_average::double precision,
  ALTER COLUMN cpu_peak TYPE DOUBLE PRECISION USING cpu_peak::double precision;
