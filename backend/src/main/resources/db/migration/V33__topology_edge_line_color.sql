-- Цвет линии связи на топологии (CSS hex); NULL — цвет по умолчанию в UI.

ALTER TABLE topology_object_edges
  ADD COLUMN line_color VARCHAR(32) NULL;
