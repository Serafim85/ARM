package com.networkscanner.backend.topology.dto;

import com.networkscanner.backend.topology.model.TopologyNodeKind;
import com.networkscanner.backend.topology.model.TopologyObjectKind;
import jakarta.validation.constraints.NotNull;

/**
 * Создание объекта топологии. Обязательные поля зависят от {@link #kind} (проверка в сервисе).
 */
public record TopologyObjectCreateRequest(
    @NotNull TopologyObjectKind kind,
    String elementId,
    String name,
    String status,
    String description,
    Long layerId,
    Long groupId,
    Double positionX,
    Double positionY,
    TopologyNodeKind nodeKind,
    Long deviceId,
    Long sourceObjectId,
    Long targetObjectId,
    Double frameWidth,
    Double frameHeight,
    /** Только {@code GROUP}: цвет рамки (#RRGGBB / #RGB) или {@code null}. */
    String frameBorderColor,
    /** Только {@code EDGE}: цвет линии (#RRGGBB / #RGB) или {@code null}. */
    String lineColor
) {
}
