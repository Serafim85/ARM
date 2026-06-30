package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MonitoringItemStatePageDto(
    List<MonitoringItemStateDto> content,
    long totalElements,
    int totalPages,
    int number,
    int size,
    boolean first,
    boolean last
) {
}
