package com.networkscanner.backend.inventory.dto;

public record BackupComparisonResult(
    String backupId,
    String backupName,
    String baselineStatus,
    String comparedAt,
    String summary
) {
}
