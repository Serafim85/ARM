ALTER TABLE dashboard_widgets
  ADD COLUMN IF NOT EXISTS name VARCHAR(255) NOT NULL DEFAULT '';

CREATE INDEX IF NOT EXISTS idx_dashboard_widgets_name_lower
  ON dashboard_widgets (LOWER(name));
