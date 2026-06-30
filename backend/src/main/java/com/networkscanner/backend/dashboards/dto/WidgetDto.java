package com.networkscanner.backend.dashboards.dto;

import java.util.List;

public record WidgetDto(
    Long id,
    Long dashboardId,
    int sortOrder,
    String name,
    String widgetType,
    int gridX,
    int gridY,
    int width,
    int height,
    int viewMode,
    Integer refreshIntervalSeconds,
    boolean showHeader,
    int borderWidthPx,
    String borderColor,
    List<WidgetFieldDto> fields
) {
}
