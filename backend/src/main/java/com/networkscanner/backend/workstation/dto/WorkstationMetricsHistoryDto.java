package com.networkscanner.backend.workstation.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkstationMetricsHistoryDto(
    String deviceKey,
    OffsetDateTime from,
    OffsetDateTime to,
    List<WorkstationMetricSeriesDto> series
) {
}
