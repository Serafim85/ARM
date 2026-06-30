package com.networkscanner.backend.users.dto;

import java.util.List;

public record MonitoringDevicesColumnsPreferenceDto(
    List<MonitoringDevicesColumnPreferenceItemDto> columns
) {
}
