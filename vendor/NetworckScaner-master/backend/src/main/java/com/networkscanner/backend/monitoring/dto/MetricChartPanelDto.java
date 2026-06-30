package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MetricChartPanelDto(
    String panelKey,
    String title,
    String graphType,
    List<String> metricNames,
    List<String> rightAxisMetricNames,
    List<MetricValueDto> points,
    List<MetricChartThresholdDto> thresholds
) {
  public MetricChartPanelDto(
      String panelKey,
      String title,
      String graphType,
      List<String> metricNames,
      List<String> rightAxisMetricNames,
      List<MetricValueDto> points
  ) {
    this(panelKey, title, graphType, metricNames, rightAxisMetricNames, points, List.of());
  }
}
