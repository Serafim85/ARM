package com.networkscanner.backend.inventory.dto;

import java.util.List;

public record DeviceBackupSnapshot(
    String deviceIp,
    String autoCompareSchedule,
    String lastAutoComparisonAt,
    BaselineConfigSummary baseline,
    List<DeviceBackupEntry> backups
) {
}
