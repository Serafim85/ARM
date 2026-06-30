package com.networkscanner.backend.topology.dto;

import com.networkscanner.backend.topology.model.TopologyNodeKind;
import com.networkscanner.backend.topology.model.TopologyObjectKind;

public record TopologyObjectDto(
    Long id,
    TopologyObjectKind kind,
    Long topologyId,
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
    String sourceElementId,
    String targetElementId,
    Double frameWidth,
    Double frameHeight,
    /** Только {@code GROUP}: цвет рамки (#RRGGBB); {@code null} — по умолчанию в UI. */
    String frameBorderColor,
    /** Только {@code EDGE}: цвет линии и стрелки (#RRGGBB); {@code null} — по умолчанию в UI. */
    String lineColor,
    /**
     * Только {@code NODE} и {@code GROUP}: цвет подложки слоя (#RRGGBB); {@code null} — без заливки в UI.
     */
    String layerBackdropColor,
    /**
     * Только {@code NODE} с привязкой к устройству: агрегат по полю {@code status} в мониторинге
     * ({@code AVAILABLE} / {@code UNAVAILABLE} / {@code UNKNOWN}); иначе {@code null}.
     */
    String deviceHostAvailability,
    /**
     * Только {@code NODE} с привязкой к устройству: {@code health_status} в мониторинге
     * ({@code NORM} / {@code WARN} / {@code CRITICAL}); иначе {@code null}.
     */
    String deviceHealthStatus,
    /**
     * Только {@code GROUP}: загружен ли фон слоя (PNG/JPEG/SVG); для остальных — {@code null}.
     */
    Boolean layerBackgroundPresent
) {
}
