ALTER TABLE monitored_devices
  ADD COLUMN IF NOT EXISTS template_ids TEXT;
