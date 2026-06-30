package com.networkscanner.backend.network.scanjobs.dto;

import com.networkscanner.backend.network.scanjobs.model.ScanJobStatus;
import java.time.OffsetDateTime;

public record ScanJobDto(
    Long id,
    String name,
    boolean enabled,
    String cron,
    OffsetDateTime lastRunAt,
    ScanJobStatus lastStatus,
    String lastError,
    int lastResultCount,
    int discoveredNotMonitoredCount,
    Long activeRunId,
    int scannedAddresses,
    int totalAddresses,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}

