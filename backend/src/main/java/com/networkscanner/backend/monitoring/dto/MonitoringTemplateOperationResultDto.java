package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateOperationResultDto(
    String message,
    MonitoringTemplateImportPreviewDto preview
) {
}
