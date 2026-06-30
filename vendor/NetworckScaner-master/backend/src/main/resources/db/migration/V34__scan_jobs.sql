-- Scan jobs (autoscanning) with last result only.
CREATE TABLE IF NOT EXISTS scan_jobs (
  id BIGSERIAL PRIMARY KEY,
  name TEXT NOT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  cron TEXT NOT NULL,
  request_json TEXT NOT NULL,
  last_run_at TIMESTAMPTZ,
  last_status TEXT,
  last_error TEXT,
  last_result_json TEXT,
  last_result_count INT NOT NULL DEFAULT 0,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scan_jobs_enabled ON scan_jobs(enabled);
