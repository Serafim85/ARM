package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

/**
 * Компактное представление ряда графика: метаданные один раз + параллельные массивы точек.
 *
 * @param t  метки времени точек, epoch millis (UTC)
 * @param v  сырые значения метрики
 * @param sv масштабированные значения (bps/Mbps, B/GB и т.п.); {@code null}, если масштабирование не применялось
 * @param valueMapName имя valuemap из шаблона; {@code null} для обычных метрик
 * @param valueMapMappings сопоставление сырого значения → текст; {@code null} для обычных метрик
 */
public record ChartSeriesDto(
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
  public ChartSeriesDto(
      String metricName,
      String displayName,
      String unit,
      String scaledUnit,
      long[] t,
      double[] v,
      double[] sv
  ) {
    this(metricName, displayName, unit, scaledUnit, t, v, sv, null, null);
  }
}
