-- Тип GROUP и принадлежность объектов к группе (group_id → объект с object_kind = GROUP).

ALTER TABLE topology_objects DROP CONSTRAINT chk_topology_objects_kind;
ALTER TABLE topology_objects ADD CONSTRAINT chk_topology_objects_kind
  CHECK (object_kind IN ('NODE', 'EDGE', 'GROUP'));

ALTER TABLE topology_objects
  ADD COLUMN group_id BIGINT REFERENCES topology_objects (id) ON DELETE SET NULL;

CREATE INDEX idx_topology_objects_group_id ON topology_objects (group_id);

CREATE TABLE topology_object_groups (
  id BIGINT PRIMARY KEY REFERENCES topology_objects (id) ON DELETE CASCADE
);
