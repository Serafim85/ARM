package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;

public record MetricHistoryRequest(
    String metricName,
    OffsetDateTime since,
    Integer limit
) {
}
