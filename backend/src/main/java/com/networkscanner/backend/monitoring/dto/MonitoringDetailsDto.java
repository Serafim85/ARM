package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;

public record MonitoringDetailsDto(
    MonitoringMetricDto cpu,
    Integer ramUsedPercent,
    Integer romUsedPercent,
    String uptime,
    String description,
    String adminContact,
    String hardwareVersion,
    String location,
    String addedAt,
    String bootVersion,
    OffsetDateTime collectedAt,
    String source,
    boolean liveMode
) {
}
