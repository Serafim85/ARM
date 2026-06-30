package com.networkscanner.backend.network.scan.dto;

import com.networkscanner.backend.network.scan.model.ScanRunStatus;

public record ScanRunStartResponse(
    long runId,
    Long scanJobId,
    ScanRunStatus status,
    int totalAddresses
) {
}
