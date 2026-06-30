package com.networkscanner.backend.topology.dto;

import com.networkscanner.backend.topology.model.TopologyNodeKind;

/**
 * Частичное обновление объекта топологии. Все поля необязательны; должен быть передан хотя бы один атрибут.
 * Координаты {@code positionX} / {@code positionY} — для узла {@code NODE} (центр) и для группы {@code GROUP} (центр рамки).
 * {@code frameWidth} / {@code frameHeight} — только для {@code GROUP}.
 * <p>{@code groupId} — назначить родительскую группу (объект с типом GROUP в этой топологии).
 * <p>{@code clearGroup} {@code true} — снять принадлежность к группе. Не сочетать с {@code groupId} в одном запросе.
 * <p>{@code nodeKind}, {@code deviceId}, {@code clearDevice} — только для узла {@code NODE}; {@code clearDevice} не сочетать с {@code deviceId}.
 * <p>{@code frameBorderColor} — только для {@code GROUP}: hex {@code #RRGGBB} или {@code #RGB}; пустая строка — сброс (цвет по умолчанию).
 * <p>{@code layerBackdropColor} — только для {@code NODE} и {@code GROUP}: hex; пустая строка — сброс.
 * <p>{@code lineColor} — только для {@code EDGE}: hex {@code #RRGGBB} или {@code #RGB}; пустая строка — сброс.
 */
public record TopologyObjectUpdateRequest(
    Double positionX,
    Double positionY,
    String name,
    Double frameWidth,
    Double frameHeight,
    String frameBorderColor,
    String layerBackdropColor,
    String lineColor,
    Long groupId,
    Boolean clearGroup,
    TopologyNodeKind nodeKind,
    Long deviceId,
    Boolean clearDevice
) {
}
