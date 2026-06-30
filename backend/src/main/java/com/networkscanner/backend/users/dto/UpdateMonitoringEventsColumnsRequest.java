package com.networkscanner.backend.users.dto;

import java.util.List;

public record UpdateMonitoringEventsColumnsRequest(
    List<MonitoringEventsColumnPreferenceItemDto> columns
) {
}
