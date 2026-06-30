package com.networkscanner.backend.dashboards.dto;

public record WidgetFieldDto(
    Long id,
    String name,
    int valueInt,
    String valueStr
) {
}
