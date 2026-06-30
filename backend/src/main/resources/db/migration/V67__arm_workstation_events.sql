-- WISLA ARM: significant workstation events (BSoD, kernel panic, etc.)

CREATE TABLE IF NOT EXISTS arm_workstation_events (
  id BIGSERIAL PRIMARY KEY,
  workstation_id BIGINT NOT NULL REFERENCES workstations(id) ON DELETE CASCADE,
  recorded_at TIMESTAMPTZ NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  severity VARCHAR(16) NOT NULL DEFAULT 'HIGH',
  message TEXT NOT NULL,
  error_code VARCHAR(128),
  error_text TEXT,
  source VARCHAR(255)
);

CREATE INDEX IF NOT EXISTS idx_arm_workstation_events_ws_time
  ON arm_workstation_events (workstation_id, recorded_at DESC);

CREATE INDEX IF NOT EXISTS idx_arm_workstation_events_type
  ON arm_workstation_events (event_type);
