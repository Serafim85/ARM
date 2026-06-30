ALTER TABLE uploaded_monitoring_templates
  ADD COLUMN IF NOT EXISTS priority INTEGER;

CREATE TABLE IF NOT EXISTS monitoring_template_priority_overrides (
  template_id VARCHAR(128) PRIMARY KEY,
  priority INTEGER NOT NULL
);
