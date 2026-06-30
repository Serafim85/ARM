package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateSummaryDto(
    String id,
    String type,
    String name,
    String description,
    String uploadedBy,
    String uploadedByDisplayName,
    String extendsTemplate,
    String vendor,
    String model,
    String modelRegex,
    String firmware,
    int priority,
    String schemaVersion,
    String packVersion,
    String templateVersion,
    MonitoringTemplateSource source,
    boolean deletable
) {
}
