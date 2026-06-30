package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;

public record MetricHistoryPoint(
    OffsetDateTime recordedAt,
    double value
) {
}
