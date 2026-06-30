CREATE TABLE IF NOT EXISTS monitored_device_interfaces (
  id BIGSERIAL PRIMARY KEY,
  device_id BIGINT NOT NULL,
  name VARCHAR(255) NOT NULL,
  description TEXT NOT NULL,
  admin_status VARCHAR(32) NOT NULL,
  oper_status VARCHAR(32) NOT NULL,
  lost VARCHAR(8) NOT NULL,
  nominal_speed VARCHAR(64) NOT NULL,
  active_speed VARCHAR(64) NOT NULL,
  purpose TEXT NOT NULL,
  mode VARCHAR(64) NOT NULL,
  kind VARCHAR(32) NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  CONSTRAINT fk_monitored_device_interfaces_device
    FOREIGN KEY (device_id) REFERENCES monitored_devices(id) ON DELETE CASCADE,
  CONSTRAINT uq_monitored_device_interfaces_device_name UNIQUE (device_id, name)
);

CREATE INDEX IF NOT EXISTS idx_monitored_device_interfaces_device
  ON monitored_device_interfaces(device_id, name);
