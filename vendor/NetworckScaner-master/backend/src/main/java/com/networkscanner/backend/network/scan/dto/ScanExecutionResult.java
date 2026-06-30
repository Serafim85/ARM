package com.networkscanner.backend.network.scan.dto;

import java.util.List;

public record ScanExecutionResult(
    List<DeviceScanResult> results,
    boolean cancelled
) {
}
