-- Async scan execution runs (manual + scheduled jobs).
CREATE TABLE IF NOT EXISTS scan_runs (
  id BIGSERIAL PRIMARY KEY,
  source TEXT NOT NULL,
  scan_job_id BIGINT REFERENCES scan_jobs(id) ON DELETE SET NULL,
  request_json TEXT NOT NULL,
  status TEXT NOT NULL,
  total_addresses INT NOT NULL DEFAULT 0,
  scanned_addresses INT NOT NULL DEFAULT 0,
  found_count INT NOT NULL DEFAULT 0,
  result_json TEXT,
  error_message TEXT,
  started_at TIMESTAMPTZ,
  finished_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_scan_runs_scan_job_id ON scan_runs(scan_job_id);
CREATE INDEX IF NOT EXISTS idx_scan_runs_status ON scan_runs(status);
CREATE INDEX IF NOT EXISTS idx_scan_runs_scan_job_active ON scan_runs(scan_job_id, status)
  WHERE status IN ('QUEUED', 'RUNNING');

ALTER TABLE scan_jobs ADD COLUMN IF NOT EXISTS active_run_id BIGINT REFERENCES scan_runs(id) ON DELETE SET NULL;
