ALTER TABLE monitored_devices ADD COLUMN IF NOT EXISTS template_version VARCHAR(64);
ALTER TABLE monitored_devices ADD COLUMN IF NOT EXISTS pack_version VARCHAR(64);
ALTER TABLE monitored_devices ADD COLUMN IF NOT EXISTS schema_version VARCHAR(32);

ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS template_id VARCHAR(128);
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS template_version VARCHAR(64);
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS pack_version VARCHAR(64);
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS item_uuid VARCHAR(64);
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS item_key VARCHAR(255);
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS instance_key VARCHAR(255);
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS discovery_rule_key VARCHAR(255);
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS unit_label VARCHAR(64);
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS value_text TEXT;
ALTER TABLE metric_values ADD COLUMN IF NOT EXISTS value_map_name VARCHAR(128);

CREATE INDEX IF NOT EXISTS idx_metric_values_item_instance_time
  ON metric_values(device_ip, item_key, instance_key, recorded_at DESC);

CREATE TABLE IF NOT EXISTS monitoring_item_state (
  device_id BIGINT NOT NULL REFERENCES monitored_devices(id) ON DELETE CASCADE,
  template_id VARCHAR(128) NOT NULL,
  item_key VARCHAR(255) NOT NULL,
  instance_key VARCHAR(255) NOT NULL DEFAULT '',
  item_uuid VARCHAR(64),
  discovery_rule_key VARCHAR(255),
  unit_label VARCHAR(64),
  value_map_name VARCHAR(128),
  numeric_value DOUBLE PRECISION,
  text_value TEXT,
  last_collected_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (device_id, item_key, instance_key)
);

CREATE INDEX IF NOT EXISTS idx_monitoring_item_state_device_rule
  ON monitoring_item_state(device_id, discovery_rule_key, instance_key);

CREATE TABLE IF NOT EXISTS monitoring_discovery_instances (
  device_id BIGINT NOT NULL REFERENCES monitored_devices(id) ON DELETE CASCADE,
  template_id VARCHAR(128) NOT NULL,
  discovery_rule_key VARCHAR(255) NOT NULL,
  instance_key VARCHAR(255) NOT NULL,
  macros_json TEXT NOT NULL,
  last_discovered_at TIMESTAMPTZ NOT NULL,
  expires_at TIMESTAMPTZ NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE,
  PRIMARY KEY (device_id, discovery_rule_key, instance_key)
);

CREATE INDEX IF NOT EXISTS idx_monitoring_discovery_instances_active
  ON monitoring_discovery_instances(device_id, discovery_rule_key, active, expires_at DESC);

ALTER TABLE monitoring_events ADD COLUMN IF NOT EXISTS trigger_uuid VARCHAR(64);
ALTER TABLE monitoring_events ADD COLUMN IF NOT EXISTS trigger_name VARCHAR(255);
ALTER TABLE monitoring_events ADD COLUMN IF NOT EXISTS instance_key VARCHAR(255);
ALTER TABLE monitoring_events ADD COLUMN IF NOT EXISTS severity VARCHAR(32);
ALTER TABLE monitoring_events ADD COLUMN IF NOT EXISTS template_version VARCHAR(64);
ALTER TABLE monitoring_events ADD COLUMN IF NOT EXISTS pack_version VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_monitoring_events_trigger_instance
  ON monitoring_events(device_id, trigger_uuid, instance_key, status);
