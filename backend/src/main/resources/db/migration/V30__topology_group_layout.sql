-- Прямоугольная область группы на топологии (центр + размеры в координатах графа).

ALTER TABLE topology_object_groups
  ADD COLUMN position_x DOUBLE PRECISION NOT NULL DEFAULT 200,
  ADD COLUMN position_y DOUBLE PRECISION NOT NULL DEFAULT 200,
  ADD COLUMN frame_width DOUBLE PRECISION NOT NULL DEFAULT 280,
  ADD COLUMN frame_height DOUBLE PRECISION NOT NULL DEFAULT 200;
