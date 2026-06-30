ALTER TABLE baseline_configs DROP CONSTRAINT IF EXISTS baseline_configs_device_ip_fkey;
ALTER TABLE device_backups DROP CONSTRAINT IF EXISTS device_backups_device_ip_fkey;

ALTER TABLE monitored_devices ADD COLUMN IF NOT EXISTS id BIGINT;

UPDATE monitored_devices SET id = sub.row_num
FROM (
  SELECT ip, ROW_NUMBER() OVER (ORDER BY ip) AS row_num FROM monitored_devices
) AS sub
WHERE monitored_devices.ip = sub.ip AND monitored_devices.id IS NULL;

CREATE SEQUENCE IF NOT EXISTS monitored_devices_id_seq;
SELECT setval(
  'monitored_devices_id_seq',
  COALESCE((SELECT MAX(id) FROM monitored_devices), 1)
);

ALTER TABLE monitored_devices ALTER COLUMN id SET DEFAULT nextval('monitored_devices_id_seq');
ALTER TABLE monitored_devices ALTER COLUMN id SET NOT NULL;
ALTER SEQUENCE monitored_devices_id_seq OWNED BY monitored_devices.id;

ALTER TABLE monitored_devices DROP CONSTRAINT IF EXISTS monitored_devices_pkey;
ALTER TABLE monitored_devices ADD PRIMARY KEY (id);
ALTER TABLE monitored_devices ADD CONSTRAINT uq_monitored_devices_ip UNIQUE (ip);

ALTER TABLE baseline_configs
  ADD CONSTRAINT baseline_configs_device_ip_fkey
  FOREIGN KEY (device_ip) REFERENCES monitored_devices (ip) ON DELETE CASCADE;

ALTER TABLE device_backups
  ADD CONSTRAINT device_backups_device_ip_fkey
  FOREIGN KEY (device_ip) REFERENCES monitored_devices (ip) ON DELETE CASCADE;
