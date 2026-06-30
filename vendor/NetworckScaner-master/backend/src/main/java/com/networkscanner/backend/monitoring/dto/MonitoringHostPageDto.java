package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MonitoringHostPageDto(
    List<MonitoringHostRowDto> content,
    long totalElements,
    int totalPages,
    int number,
    int size,
    boolean first,
    boolean last,
    long availableCount,
    long unavailableCount,
    long unknownCount
) {
}
