-- Цвет рамки группы на топологии (CSS hex, например #64748b); NULL — цвет по умолчанию в UI.

ALTER TABLE topology_object_groups
  ADD COLUMN frame_border_color VARCHAR(32) NULL;
