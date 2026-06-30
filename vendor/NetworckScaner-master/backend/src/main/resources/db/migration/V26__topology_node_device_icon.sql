ALTER TABLE topology_object_nodes
  ADD COLUMN device_id BIGINT REFERENCES monitored_devices (id) ON DELETE SET NULL;

ALTER TABLE topology_object_nodes
  ADD COLUMN icon VARCHAR(255);

ALTER TABLE topology_object_nodes
  ADD COLUMN node_kind VARCHAR(32);

CREATE INDEX idx_topology_object_nodes_device_id ON topology_object_nodes (device_id);

ALTER TABLE topology_object_nodes ADD CONSTRAINT chk_topology_node_kind CHECK (
  node_kind IS NULL
  OR node_kind IN (
    'NETWORK',
    'SERVER',
    'PRINTER',
    'ROUTER',
    'SWITCH',
    'PC',
    'NOTEBOOK',
    'FIREWALL'
  )
);
