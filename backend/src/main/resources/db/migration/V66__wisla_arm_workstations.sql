-- WISLA ARM: workstations registry + agent credentials (MVP)

CREATE TABLE IF NOT EXISTS workstations (
  id BIGSERIAL PRIMARY KEY,
  hostname VARCHAR(255) NOT NULL,
  display_name VARCHAR(255),
  os_type VARCHAR(32) NOT NULL DEFAULT 'unknown',
  primary_ip VARCHAR(64),
  agent_version VARCHAR(64),
  status VARCHAR(32) NOT NULL DEFAULT 'offline',
  last_seen_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_workstations_hostname UNIQUE (hostname)
);

CREATE INDEX IF NOT EXISTS idx_workstations_last_seen ON workstations (last_seen_at DESC);
CREATE INDEX IF NOT EXISTS idx_workstations_status ON workstations (status);

CREATE TABLE IF NOT EXISTS agent_credentials (
  id BIGSERIAL PRIMARY KEY,
  workstation_id BIGINT REFERENCES workstations(id) ON DELETE CASCADE,
  api_key_hash VARCHAR(255) NOT NULL,
  label VARCHAR(255),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  CONSTRAINT uq_agent_credentials_api_key_hash UNIQUE (api_key_hash)
);

CREATE INDEX IF NOT EXISTS idx_agent_credentials_workstation
  ON agent_credentials (workstation_id);

CREATE TABLE IF NOT EXISTS arm_log_events (
  id BIGSERIAL PRIMARY KEY,
  workstation_id BIGINT NOT NULL REFERENCES workstations(id) ON DELETE CASCADE,
  recorded_at TIMESTAMPTZ NOT NULL,
  level VARCHAR(16) NOT NULL,
  message TEXT NOT NULL,
  source VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_arm_log_events_workstation_time
  ON arm_log_events (workstation_id, recorded_at DESC);
