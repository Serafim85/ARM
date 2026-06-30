package com.networkscanner.backend.network.scan.dto;

import com.networkscanner.backend.network.scan.model.ScanRunSource;
import com.networkscanner.backend.network.scan.model.ScanRunStatus;
import java.time.OffsetDateTime;

public record ScanRunDto(
    long runId,
    ScanRunSource source,
    Long scanJobId,
    ScanRunStatus status,
    int totalAddresses,
    int scannedAddresses,
    int foundCount,
    String errorMessage,
    OffsetDateTime startedAt,
    OffsetDateTime finishedAt,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
