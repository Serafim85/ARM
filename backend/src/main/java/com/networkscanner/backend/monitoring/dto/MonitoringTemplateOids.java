package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

public record MonitoringTemplateOids(
    Map<String, String> discovery,
    Map<String, String> details,
    Map<String, String> interfaces
) {
}
