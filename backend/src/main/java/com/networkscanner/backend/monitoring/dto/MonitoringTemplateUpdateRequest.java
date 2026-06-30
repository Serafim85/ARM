package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateUpdateRequest(
    String vendor,
    String model,
    String firmware,
    Integer priority
) {
}
