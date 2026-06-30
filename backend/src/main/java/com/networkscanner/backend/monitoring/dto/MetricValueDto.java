package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;

public record MetricValueDto(
    OffsetDateTime recordedAt,
    String deviceIp,
    String metricName,
    double metricValue,
    String unit,
    /** Human-readable name from monitoring template item; {@code null} if unknown. */
    String metricDisplayName,
    Double scaledMetricValue,
    String scaledUnit,
    String scaledDisplayValue
) {
  public MetricValueDto(
      OffsetDateTime recordedAt,
      String deviceIp,
      String metricName,
      double metricValue,
      String unit,
      String metricDisplayName
  ) {
    this(recordedAt, deviceIp, metricName, metricValue, unit, metricDisplayName, null, null, null);
  }
}
