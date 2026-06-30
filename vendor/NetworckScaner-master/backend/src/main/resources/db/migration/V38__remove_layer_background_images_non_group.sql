-- Фон-картинка только у групп: убираем корневой слой и NODE.

DROP TABLE IF EXISTS topology_root_layer_backgrounds;

ALTER TABLE topologies
  DROP COLUMN IF EXISTS root_layer_background_present;

DELETE FROM topology_object_layer_backgrounds lb
USING topology_objects o
WHERE lb.object_id = o.id AND o.object_kind = 'NODE';

ALTER TABLE topology_object_nodes
  DROP COLUMN IF EXISTS layer_background_present;
