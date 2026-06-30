-- Объекты топологии (узлы, рёбра и др.) с наследованием JOINED.
-- element_id — обязательный data.id для Cytoscape; layer_id — родительский объект (вложенный слой).

CREATE TABLE topology_objects (
  id           BIGSERIAL    PRIMARY KEY,
  object_kind  VARCHAR(32)  NOT NULL,
  topology_id  BIGINT       NOT NULL REFERENCES topologies (id) ON DELETE CASCADE,
  layer_id     BIGINT       REFERENCES topology_objects (id) ON DELETE SET NULL,
  element_id   VARCHAR(255) NOT NULL,
  name         VARCHAR(512),
  status       VARCHAR(64),
  description  TEXT,
  CONSTRAINT uq_topology_objects_topology_element UNIQUE (topology_id, element_id),
  CONSTRAINT chk_topology_objects_kind CHECK (object_kind IN ('NODE', 'EDGE'))
);

CREATE INDEX idx_topology_objects_topology_id ON topology_objects (topology_id);
CREATE INDEX idx_topology_objects_layer_id ON topology_objects (layer_id);

CREATE TABLE topology_object_nodes (
  id          BIGINT PRIMARY KEY REFERENCES topology_objects (id) ON DELETE CASCADE,
  position_x  DOUBLE PRECISION,
  position_y  DOUBLE PRECISION
);

CREATE TABLE topology_object_edges (
  id         BIGINT PRIMARY KEY REFERENCES topology_objects (id) ON DELETE CASCADE,
  source_id  BIGINT NOT NULL REFERENCES topology_objects (id) ON DELETE CASCADE,
  target_id  BIGINT NOT NULL REFERENCES topology_objects (id) ON DELETE CASCADE,
  CONSTRAINT chk_topology_edge_not_self CHECK (source_id <> target_id)
);

CREATE INDEX idx_topology_object_edges_source ON topology_object_edges (source_id);
CREATE INDEX idx_topology_object_edges_target ON topology_object_edges (target_id);
