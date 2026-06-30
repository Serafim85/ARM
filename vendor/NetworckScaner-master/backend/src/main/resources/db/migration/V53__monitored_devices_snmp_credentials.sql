ALTER TABLE monitored_devices
  ADD COLUMN IF NOT EXISTS snmp_version VARCHAR(8),
  ADD COLUMN IF NOT EXISTS snmp_community VARCHAR(255),
  ADD COLUMN IF NOT EXISTS snmp_security_username VARCHAR(128),
  ADD COLUMN IF NOT EXISTS snmp_auth_protocol VARCHAR(16),
  ADD COLUMN IF NOT EXISTS snmp_auth_password VARCHAR(255),
  ADD COLUMN IF NOT EXISTS snmp_privacy_protocol VARCHAR(16),
  ADD COLUMN IF NOT EXISTS snmp_privacy_password VARCHAR(255);
