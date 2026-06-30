package com.networkscanner.backend.topology.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Одно изменение координат/рамки в пакетном сохранении раскладки топологии.
 */
public record TopologyLayoutPatchItem(
    @NotNull Long objectId,
    Double positionX,
    Double positionY,
    Double frameWidth,
    Double frameHeight
) {}
