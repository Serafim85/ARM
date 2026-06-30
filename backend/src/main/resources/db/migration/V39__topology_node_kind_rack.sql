-- Разделяем «стойку» (RACK) и «сервер» (SERVER); ранее оба отображались как SERVER.
ALTER TABLE topology_object_nodes DROP CONSTRAINT chk_topology_node_kind;

UPDATE topology_object_nodes SET node_kind = 'RACK' WHERE node_kind = 'SERVER';

ALTER TABLE topology_object_nodes ADD CONSTRAINT chk_topology_node_kind CHECK (
  node_kind IS NULL
  OR node_kind IN (
    'NETWORK',
    'RACK',
    'SERVER',
    'PRINTER',
    'ROUTER',
    'SWITCH',
    'PC',
    'NOTEBOOK',
    'FIREWALL'
  )
);
