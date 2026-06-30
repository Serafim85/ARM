package com.networkscanner.backend.users.dto;

import java.util.List;

public record MonitoringEventsColumnsPreferenceDto(
    List<MonitoringEventsColumnPreferenceItemDto> columns
) {
}
