package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record DeviceMetricsHistoryResponseDto(
    List<MetricChartPanelDto> chartPanels,
    int totalChartPanels
) {
}
