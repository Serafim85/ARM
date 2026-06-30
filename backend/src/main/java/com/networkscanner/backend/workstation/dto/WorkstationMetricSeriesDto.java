package com.networkscanner.backend.workstation.dto;

import java.util.List;

public record WorkstationMetricSeriesDto(
    String metricKey,
    String displayName,
    String unit,
    List<WorkstationMetricPointDto> points
) {
}
