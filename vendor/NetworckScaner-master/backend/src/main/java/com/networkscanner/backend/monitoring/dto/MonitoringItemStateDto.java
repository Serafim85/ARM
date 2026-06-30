package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record MonitoringItemStateDto(
    String itemKey,
    String itemDisplayName,
    String instanceKey,
    Double numericValue,
    String textValue,
    String unitLabel,
    Double scaledNumericValue,
    String scaledUnitLabel,
    String scaledDisplayValue,
    String valueMapName,
    Map<String, String> valueMapMappings,
    String presentationValue,
    String preprocessingStatus,
    String preprocessingNote,
    OffsetDateTime lastCollectedAt,
    List<MetricChartThresholdDto> thresholds
) {
  public MonitoringItemStateDto(
      String itemKey,
      String instanceKey,
      Double numericValue,
      String textValue,
      String unitLabel,
      String valueMapName,
      String presentationValue,
      String preprocessingStatus,
      String preprocessingNote,
      OffsetDateTime lastCollectedAt
  ) {
    this(
        itemKey,
        null,
        instanceKey,
        numericValue,
        textValue,
        unitLabel,
        null,
        null,
        null,
        valueMapName,
        null,
        presentationValue,
        preprocessingStatus,
        preprocessingNote,
        lastCollectedAt,
        List.of()
    );
  }
}
