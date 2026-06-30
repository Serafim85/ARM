package com.networkscanner.backend.monitoring.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MonitoringMetricsBatchSeriesRequest(
    @NotNull @Positive Long deviceId,
    @NotBlank String metricName
) {
}
