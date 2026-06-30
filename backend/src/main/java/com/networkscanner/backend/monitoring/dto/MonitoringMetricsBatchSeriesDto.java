package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MonitoringMetricsBatchSeriesDto(
    Long deviceId,
    String metricName,
    List<MetricValueDto> points
) {
}
