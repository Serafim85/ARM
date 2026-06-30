package com.networkscanner.backend.monitoring.dto;

import java.util.List;
import java.util.Map;

/**
 * Порог триггера для визуализации на графике или в таблице текущего состояния.
 */
public record MetricChartThresholdDto(
    String metricName,
    String instanceKey,
    String triggerName,
    String triggerUuid,
    String thresholdLevel,
    /** Снимок порога на конец периода (или «сейчас» для таблицы состояния). */
    double thresholdValue,
    Double scaledThresholdValue,
    String operator,
    /** {@code true}, если порог вычисляется из метрик (например {@code 0.9*last(speed)}). */
    boolean dynamic,
    /** Временной ряд порога для графика (epoch millis). */
    List<Long> seriesT,
    List<Double> seriesV,
    List<Double> seriesSv,
    /** Valuemap метрики для текстовой подписи порога в UI. */
    Map<String, String> valueMapMappings
) {
  public MetricChartThresholdDto(
      String metricName,
      String instanceKey,
      String triggerName,
      String triggerUuid,
      String thresholdLevel,
      double thresholdValue,
      Double scaledThresholdValue,
      String operator
  ) {
    this(
        metricName,
        instanceKey,
        triggerName,
        triggerUuid,
        thresholdLevel,
        thresholdValue,
        scaledThresholdValue,
        operator,
        false,
        null,
        null,
        null,
        null
    );
  }

  public MetricChartThresholdDto(
      String metricName,
      String instanceKey,
      String triggerName,
      String triggerUuid,
      String thresholdLevel,
      double thresholdValue,
      Double scaledThresholdValue,
      String operator,
      boolean dynamic,
      List<Long> seriesT,
      List<Double> seriesV,
      List<Double> seriesSv
  ) {
    this(
        metricName,
        instanceKey,
        triggerName,
        triggerUuid,
        thresholdLevel,
        thresholdValue,
        scaledThresholdValue,
        operator,
        dynamic,
        seriesT,
        seriesV,
        seriesSv,
        null
    );
  }
}
