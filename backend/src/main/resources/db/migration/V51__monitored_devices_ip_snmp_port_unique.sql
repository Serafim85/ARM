ALTER TABLE baseline_configs
  DROP CONSTRAINT IF EXISTS baseline_configs_device_ip_fkey;

ALTER TABLE device_backups
  DROP CONSTRAINT IF EXISTS device_backups_device_ip_fkey;

ALTER TABLE monitored_devices
  DROP CONSTRAINT IF EXISTS uq_monitored_devices_ip;

DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM pg_constraint
    WHERE conname = 'monitored_devices_ip_key'
      AND conrelid = 'monitored_devices'::regclass
  ) THEN
    ALTER TABLE monitored_devices DROP CONSTRAINT monitored_devices_ip_key;
  END IF;
END $$;

ALTER TABLE monitored_devices
  ADD CONSTRAINT uq_monitored_devices_ip_snmp_port UNIQUE (ip, snmp_port);
