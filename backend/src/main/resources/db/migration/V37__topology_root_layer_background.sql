-- Подложка корневого уровня топологии (без объекта-родителя в topology_objects).

ALTER TABLE topologies
  ADD COLUMN root_layer_backdrop_color VARCHAR(32) NULL;

ALTER TABLE topologies
  ADD COLUMN root_layer_background_present BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE topology_root_layer_backgrounds (
  topology_id   BIGINT       PRIMARY KEY REFERENCES topologies (id) ON DELETE CASCADE,
  content_type  VARCHAR(64)  NOT NULL,
  image_data    BYTEA        NOT NULL
);
