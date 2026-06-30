CREATE TABLE IF NOT EXISTS uploaded_monitoring_templates (
  id BIGSERIAL PRIMARY KEY,
  template_id VARCHAR(128) NOT NULL UNIQUE,
  extends_template VARCHAR(128),
  original_filename VARCHAR(255) NOT NULL,
  manifest_yaml TEXT NOT NULL,
  template_file_name VARCHAR(255) NOT NULL,
  template_yaml TEXT NOT NULL,
  uploaded_by VARCHAR(255),
  uploaded_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_uploaded_monitoring_templates_extends
  ON uploaded_monitoring_templates(extends_template);
