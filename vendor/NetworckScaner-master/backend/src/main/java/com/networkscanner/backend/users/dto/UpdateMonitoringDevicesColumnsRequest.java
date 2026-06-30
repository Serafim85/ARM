package com.networkscanner.backend.users.dto;

import java.util.List;

public record UpdateMonitoringDevicesColumnsRequest(
    List<MonitoringDevicesColumnPreferenceItemDto> columns
) {
}
