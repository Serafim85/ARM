-- Фон слоя (GROUP): флаг в основной таблице без LOB в типичном SELECT; байты в отдельной таблице.

ALTER TABLE topology_object_groups
  ADD COLUMN layer_background_present BOOLEAN NOT NULL DEFAULT FALSE;

CREATE TABLE topology_group_layer_backgrounds (
  group_id      BIGINT       PRIMARY KEY REFERENCES topology_object_groups (id) ON DELETE CASCADE,
  content_type  VARCHAR(64)  NOT NULL,
  image_data    BYTEA        NOT NULL
);
