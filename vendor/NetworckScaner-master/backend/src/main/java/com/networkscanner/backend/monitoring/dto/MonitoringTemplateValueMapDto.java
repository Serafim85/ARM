package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

public record MonitoringTemplateValueMapDto(
    String name,
    Map<String, String> mappings
) {
}
