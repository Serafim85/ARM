ALTER TABLE users
  ADD COLUMN default_topology_id BIGINT;

ALTER TABLE users
  ADD CONSTRAINT fk_users_default_topology
  FOREIGN KEY (default_topology_id)
  REFERENCES topologies (id)
  ON DELETE SET NULL;

CREATE INDEX idx_users_default_topology_id ON users (default_topology_id);
