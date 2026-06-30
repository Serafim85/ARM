package com.networkscanner.backend.monitoring.dto;

import java.util.List;

/** Панель графика в компактном формате: ряды как {@link ChartSeriesDto}. */
public record CompactChartPanelDto(
    String panelKey,
    String title,
    String graphType,
    List<String> metricNames,
    List<String> rightAxisMetricNames,
    List<ChartSeriesDto> series,
    List<MetricChartThresholdDto> thresholds
) {
}
