ALTER TABLE access_profiles
  ADD COLUMN IF NOT EXISTS snmp_v1_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS snmp_v1_port INTEGER,
  ADD COLUMN IF NOT EXISTS snmp_v1_community VARCHAR(512),
  ADD COLUMN IF NOT EXISTS snmp_v2_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS snmp_v2_port INTEGER,
  ADD COLUMN IF NOT EXISTS snmp_v2_community VARCHAR(512),
  ADD COLUMN IF NOT EXISTS snmp_v3_enabled BOOLEAN NOT NULL DEFAULT FALSE,
  ADD COLUMN IF NOT EXISTS snmp_v3_port INTEGER,
  ADD COLUMN IF NOT EXISTS snmp_v3_security_username VARCHAR(255),
  ADD COLUMN IF NOT EXISTS snmp_v3_auth_protocol VARCHAR(32),
  ADD COLUMN IF NOT EXISTS snmp_v3_auth_password VARCHAR(512),
  ADD COLUMN IF NOT EXISTS snmp_v3_privacy_protocol VARCHAR(32),
  ADD COLUMN IF NOT EXISTS snmp_v3_privacy_password VARCHAR(512);

UPDATE access_profiles
SET
  snmp_v1_enabled = snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v1', '1'),
  snmp_v1_port = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v1', '1') THEN COALESCE(snmp_port, 161)
    ELSE NULL
  END,
  snmp_v1_community = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v1', '1') THEN snmp_community
    ELSE NULL
  END,
  snmp_v2_enabled = snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v2c', 'v2', '2'),
  snmp_v2_port = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v2c', 'v2', '2') THEN COALESCE(snmp_port, 161)
    ELSE NULL
  END,
  snmp_v2_community = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v2c', 'v2', '2') THEN snmp_community
    ELSE NULL
  END,
  snmp_v3_enabled = snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v3', '3'),
  snmp_v3_port = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v3', '3') THEN COALESCE(snmp_port, 161)
    ELSE NULL
  END,
  snmp_v3_security_username = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v3', '3') THEN snmp_security_username
    ELSE NULL
  END,
  snmp_v3_auth_protocol = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v3', '3') THEN snmp_auth_protocol
    ELSE NULL
  END,
  snmp_v3_auth_password = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v3', '3') THEN snmp_auth_password
    ELSE NULL
  END,
  snmp_v3_privacy_protocol = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v3', '3') THEN snmp_privacy_protocol
    ELSE NULL
  END,
  snmp_v3_privacy_password = CASE
    WHEN snmp_enabled AND LOWER(COALESCE(snmp_version, 'v2c')) IN ('v3', '3') THEN snmp_privacy_password
    ELSE NULL
  END;

ALTER TABLE access_profiles
  DROP COLUMN IF EXISTS snmp_enabled,
  DROP COLUMN IF EXISTS snmp_version,
  DROP COLUMN IF EXISTS snmp_port,
  DROP COLUMN IF EXISTS snmp_community,
  DROP COLUMN IF EXISTS snmp_security_username,
  DROP COLUMN IF EXISTS snmp_auth_protocol,
  DROP COLUMN IF EXISTS snmp_auth_password,
  DROP COLUMN IF EXISTS snmp_privacy_protocol,
  DROP COLUMN IF EXISTS snmp_privacy_password;
