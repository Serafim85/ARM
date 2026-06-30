package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateMatch(
    String vendor,
    String modelRegex
) {
}
