ALTER TABLE dashboard_widgets
  ADD COLUMN IF NOT EXISTS grid_x INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS grid_y INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS width INTEGER NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS height INTEGER NOT NULL DEFAULT 2,
  ADD COLUMN IF NOT EXISTS view_mode INTEGER NOT NULL DEFAULT 0,
  ADD COLUMN IF NOT EXISTS refresh_interval_seconds INTEGER,
  ADD COLUMN IF NOT EXISTS show_header BOOLEAN NOT NULL DEFAULT TRUE;

CREATE TABLE IF NOT EXISTS dashboard_widget_fields (
  id BIGSERIAL PRIMARY KEY,
  widget_id BIGINT NOT NULL REFERENCES dashboard_widgets (id) ON DELETE CASCADE,
  name VARCHAR(255) NOT NULL,
  value_int INTEGER NOT NULL DEFAULT 0,
  value_str VARCHAR(2048) NOT NULL DEFAULT ''
);

CREATE INDEX IF NOT EXISTS idx_dashboard_widget_fields_widget_id
  ON dashboard_widget_fields (widget_id);

CREATE INDEX IF NOT EXISTS idx_dashboard_widget_fields_widget_name
  ON dashboard_widget_fields (widget_id, name);
