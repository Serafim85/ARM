ALTER TABLE uploaded_monitoring_templates
  ADD COLUMN IF NOT EXISTS vendor VARCHAR(255),
  ADD COLUMN IF NOT EXISTS model VARCHAR(255),
  ADD COLUMN IF NOT EXISTS model_regex VARCHAR(512),
  ADD COLUMN IF NOT EXISTS firmware VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_uploaded_monitoring_templates_vendor
  ON uploaded_monitoring_templates(vendor);

CREATE INDEX IF NOT EXISTS idx_uploaded_monitoring_templates_model
  ON uploaded_monitoring_templates(model);
