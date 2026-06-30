package com.networkscanner.backend.monitoring.dto;

import java.util.List;

/** Компактный ответ истории метрик устройства для SPA (графики). */
public record CompactMetricsHistoryResponseDto(
    List<CompactChartPanelDto> chartPanels,
    int totalChartPanels
) {
}
