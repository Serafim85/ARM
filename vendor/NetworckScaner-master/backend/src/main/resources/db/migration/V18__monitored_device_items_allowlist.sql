ALTER TABLE monitored_devices
  ADD COLUMN IF NOT EXISTS item_allowlist_initialized BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE IF NOT EXISTS monitored_device_items (
  device_id BIGINT NOT NULL REFERENCES monitored_devices(id) ON DELETE CASCADE,
  item_uuid VARCHAR(32) NOT NULL,
  instance_key VARCHAR(255) NOT NULL DEFAULT '',
  item_key VARCHAR(255) NOT NULL,
  name VARCHAR(255) NOT NULL,
  item_type VARCHAR(64) NOT NULL,
  discovery_prototype BOOLEAN NOT NULL DEFAULT FALSE,
  discovery_rule_key VARCHAR(255),
  source_template_id VARCHAR(128),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (device_id, item_uuid, instance_key)
);

CREATE INDEX IF NOT EXISTS idx_monitored_device_items_device_item_key
  ON monitored_device_items(device_id, item_key);

CREATE INDEX IF NOT EXISTS idx_monitored_device_items_device_uuid
  ON monitored_device_items(device_id, item_uuid);
