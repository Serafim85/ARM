package com.networkscanner.backend.workstation.dto;

import java.util.List;

public record WorkstationPageDto(
    List<WorkstationListItemDto> content,
    long totalElements,
    int totalPages,
    int number,
    int size,
    boolean first,
    boolean last,
    long onlineCount,
    long offlineCount
) {
}
