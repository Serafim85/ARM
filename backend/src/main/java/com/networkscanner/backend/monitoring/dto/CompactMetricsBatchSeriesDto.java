package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

/** Компактный ряд истории метрик для дашборд-виджета GRAPH (batch). */
public record CompactMetricsBatchSeriesDto(
    Long deviceId,
    String metricName,
    String displayName,
    String unit,
    String scaledUnit,
    long[] t,
    double[] v,
    double[] sv,
    String valueMapName,
    Map<String, String> valueMapMappings
) {
  public CompactMetricsBatchSeriesDto(
      Long deviceId,
      String metricName,
      String displayName,
      String unit,
      String scaledUnit,
      long[] t,
      double[] v,
      double[] sv
  ) {
    this(deviceId, metricName, displayName, unit, scaledUnit, t, v, sv, null, null);
  }
}
