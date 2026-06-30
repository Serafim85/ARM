package com.networkscanner.backend.integration.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProbeBootstrapPageResponse(
    String schemaVersion,
    String sourceSystem,
    OffsetDateTime generatedAt,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean first,
    boolean last,
    List<ProbeBootstrapPayload> items
) {
}
