CREATE EXTENSION IF NOT EXISTS timescaledb;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS users (
  id BIGSERIAL PRIMARY KEY,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,
  display_name VARCHAR(255) NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS user_roles (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role_name VARCHAR(50) NOT NULL,
  PRIMARY KEY (user_id, role_name)
);

CREATE TABLE IF NOT EXISTS monitored_devices (
  ip VARCHAR(64) PRIMARY KEY,
  host_name VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  serial_number VARCHAR(255) NOT NULL,
  mac_address VARCHAR(255) NOT NULL,
  vendor VARCHAR(255) NOT NULL,
  model VARCHAR(255) NOT NULL,
  firmware_version VARCHAR(255) NOT NULL,
  polling_status VARCHAR(255) NOT NULL,
  status VARCHAR(255) NOT NULL,
  group_name VARCHAR(255) NOT NULL,
  availability_json TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE monitored_devices ADD COLUMN IF NOT EXISTS template_id VARCHAR(128);
ALTER TABLE monitored_devices ADD COLUMN IF NOT EXISTS effective_template_id VARCHAR(128);

CREATE TABLE IF NOT EXISTS baseline_configs (
  id BIGSERIAL PRIMARY KEY,
  device_ip VARCHAR(64) NOT NULL UNIQUE REFERENCES monitored_devices(ip) ON DELETE CASCADE,
  file_name VARCHAR(255) NOT NULL,
  configured_at TIMESTAMPTZ NOT NULL,
  source VARCHAR(255) NOT NULL,
  content TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS device_backups (
  id VARCHAR(64) PRIMARY KEY,
  device_ip VARCHAR(64) NOT NULL REFERENCES monitored_devices(ip) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  source VARCHAR(255) NOT NULL,
  size VARCHAR(64) NOT NULL,
  status VARCHAR(64) NOT NULL,
  baseline_status VARCHAR(64) NOT NULL,
  comparison_summary TEXT,
  compared_at TIMESTAMPTZ,
  content TEXT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_device_backups_device_ip_created_at
  ON device_backups(device_ip, created_at DESC);

CREATE TABLE IF NOT EXISTS availability_history (
  recorded_at TIMESTAMPTZ NOT NULL,
  device_ip VARCHAR(64) NOT NULL,
  host_status VARCHAR(64) NOT NULL,
  icmp_active BOOLEAN NOT NULL,
  snmp_active BOOLEAN NOT NULL,
  ssh_active BOOLEAN NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_availability_history_device_time
  ON availability_history(device_ip, recorded_at DESC);

SELECT create_hypertable('availability_history', 'recorded_at', if_not_exists => TRUE);

CREATE TABLE IF NOT EXISTS telemetry_history (
  recorded_at TIMESTAMPTZ NOT NULL,
  device_ip VARCHAR(64) NOT NULL,
  cpu_usage NUMERIC(5,2) NOT NULL,
  ram_usage NUMERIC(5,2) NOT NULL,
  rom_usage NUMERIC(5,2) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_telemetry_history_device_time
  ON telemetry_history(device_ip, recorded_at DESC);

SELECT create_hypertable('telemetry_history', 'recorded_at', if_not_exists => TRUE);

INSERT INTO users (email, password_hash, display_name, enabled)
VALUES
  ('admin@example.com', crypt('password', gen_salt('bf')), 'System Administrator', TRUE),
  ('operator@example.com', crypt('operator123', gen_salt('bf')), 'Network Operator', TRUE),
  ('viewer@example.com', crypt('viewer123', gen_salt('bf')), 'Monitoring Viewer', TRUE)
ON CONFLICT (email) DO NOTHING;

INSERT INTO user_roles (user_id, role_name)
SELECT id, 'ADMIN' FROM users WHERE email = 'admin@example.com'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_name)
SELECT id, 'OPERATOR' FROM users WHERE email = 'operator@example.com'
ON CONFLICT DO NOTHING;

INSERT INTO user_roles (user_id, role_name)
SELECT id, 'VIEWER' FROM users WHERE email = 'viewer@example.com'
ON CONFLICT DO NOTHING;
