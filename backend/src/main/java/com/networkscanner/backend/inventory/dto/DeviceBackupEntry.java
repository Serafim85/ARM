package com.networkscanner.backend.inventory.dto;

public record DeviceBackupEntry(
    String id,
    String name,
    String createdAt,
    String source,
    String size,
    String status,
    String baselineStatus,
    String comparisonSummary,
    String comparedAt
) {
}
