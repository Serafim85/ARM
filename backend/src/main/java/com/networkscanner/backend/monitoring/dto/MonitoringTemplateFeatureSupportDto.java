package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateFeatureSupportDto(
    String key,
    String title,
    boolean presentInTemplate,
    boolean importSupported,
    boolean runtimeSupported,
    boolean apiSupported,
    boolean uiSupported,
    String notes
) {
}
