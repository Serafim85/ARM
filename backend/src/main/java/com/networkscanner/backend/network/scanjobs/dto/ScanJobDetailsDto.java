package com.networkscanner.backend.network.scanjobs.dto;

import com.networkscanner.backend.network.scanjobs.model.ScanJobStatus;
import java.time.OffsetDateTime;

public record ScanJobDetailsDto(
    Long id,
    String name,
    boolean enabled,
    String cron,
    ScanJobRequest request,
    OffsetDateTime lastRunAt,
    ScanJobStatus lastStatus,
    String lastError,
    int lastResultCount,
    int discoveredNotMonitoredCount,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

