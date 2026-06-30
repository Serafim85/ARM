-- Унификация фона слоя: любой topology_objects (NODE/GROUP), не только группа.
-- Подложка: цвет без изображения (hex).

CREATE TABLE topology_object_layer_backgrounds (
  object_id     BIGINT       PRIMARY KEY REFERENCES topology_objects (id) ON DELETE CASCADE,
  content_type  VARCHAR(64)  NOT NULL,
  image_data    BYTEA        NOT NULL
);

INSERT INTO topology_object_layer_backgrounds (object_id, content_type, image_data)
SELECT group_id, content_type, image_data FROM topology_group_layer_backgrounds;

DROP TABLE topology_group_layer_backgrounds;

ALTER TABLE topology_object_nodes
  ADD COLUMN layer_background_present BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE topology_object_nodes
  ADD COLUMN layer_backdrop_color VARCHAR(32) NULL;

ALTER TABLE topology_object_groups
  ADD COLUMN layer_backdrop_color VARCHAR(32) NULL;
