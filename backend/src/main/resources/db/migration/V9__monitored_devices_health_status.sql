ALTER TABLE monitored_devices ADD COLUMN IF NOT EXISTS health_status VARCHAR(32);

UPDATE monitored_devices
SET health_status = 'NORM'
WHERE health_status IS NULL;

ALTER TABLE monitored_devices
  ALTER COLUMN health_status SET DEFAULT 'NORM';

ALTER TABLE monitored_devices
  ALTER COLUMN health_status SET NOT NULL;
