-- High-load indexes for writer/evaluator hot paths.
-- Keep operations idempotent to simplify repeated environment provisioning.

CREATE INDEX IF NOT EXISTS idx_monitoring_events_open_lookup
  ON monitoring_events (
    device_id,
    status,
    trigger_uuid,
    COALESCE(instance_key, ''),
    threshold_level
  );

CREATE INDEX IF NOT EXISTS idx_monitoring_events_open_breach
  ON monitoring_events (device_id, status, breach_started_at DESC);
