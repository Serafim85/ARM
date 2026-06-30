ALTER TABLE monitored_devices
  ADD COLUMN tags_json TEXT NOT NULL DEFAULT '[]';

