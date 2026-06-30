ALTER TABLE monitored_devices
  ADD COLUMN IF NOT EXISTS snmp_port INTEGER;
