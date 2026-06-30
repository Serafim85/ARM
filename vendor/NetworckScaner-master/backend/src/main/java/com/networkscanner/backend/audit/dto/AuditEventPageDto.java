package com.networkscanner.backend.audit.dto;

import java.util.List;

public record AuditEventPageDto(
    List<AuditEventDto> content,
    long totalElements,
    int totalPages,
    int number,
    int size,
    boolean first,
    boolean last
) {
}
