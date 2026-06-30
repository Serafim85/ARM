package com.networkscanner.backend.topology.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.networkscanner.backend.topology.model.TopologyVisibility;
import java.time.OffsetDateTime;
import java.util.Set;

public record TopologyDto(
    Long id,
    Long ownerId,
    String name,
    TopologyVisibility visibility,
    boolean autosave,
    boolean autoCenterOnResize,
    Set<Long> sharedUserIds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    JsonNode document,
    String rootLayerBackdropColor
) {
}
