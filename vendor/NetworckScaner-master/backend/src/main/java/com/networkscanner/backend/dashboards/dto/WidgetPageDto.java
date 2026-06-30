package com.networkscanner.backend.dashboards.dto;

import java.util.List;

public record WidgetPageDto(
    List<WidgetDto> content,
    long totalElements,
    int totalPages,
    int number,
    int size,
    boolean first,
    boolean last
) {
}
