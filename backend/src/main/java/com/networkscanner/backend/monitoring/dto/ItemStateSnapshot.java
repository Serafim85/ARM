package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;

public record ItemStateSnapshot(
    String templateId,
    String itemKey,
    String instanceKey,
    Double numericValue,
    String textValue,
    String unitLabel,
    String valueMapName,
    String preprocessingStatus,
    String preprocessingNote,
    OffsetDateTime lastCollectedAt
) {
}
