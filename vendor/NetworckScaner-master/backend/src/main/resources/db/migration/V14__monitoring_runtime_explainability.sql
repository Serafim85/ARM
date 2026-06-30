ALTER TABLE monitoring_item_state
  ADD COLUMN IF NOT EXISTS preprocessing_status VARCHAR(32),
  ADD COLUMN IF NOT EXISTS preprocessing_note TEXT;

ALTER TABLE monitoring_events
  ADD COLUMN IF NOT EXISTS trigger_expression TEXT,
  ADD COLUMN IF NOT EXISTS recovery_expression TEXT,
  ADD COLUMN IF NOT EXISTS recovery_path VARCHAR(64);
