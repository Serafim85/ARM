package com.networkscanner.backend.users.dto;

import java.util.Map;

public record ChartUiPreferencesDto(
    String deviceMetricsLegendPlacement,
    String deviceMetricsBaseColor,
    Map<String, String> dashboardGraphLegendPlacements,
    String deviceMetricsPeriod,
    String deviceMetricsLayout,
    String deviceMetricsCustomFrom,
    String deviceMetricsCustomTo
) {
}
