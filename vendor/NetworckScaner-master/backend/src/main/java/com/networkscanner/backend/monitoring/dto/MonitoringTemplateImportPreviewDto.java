package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateImportPreviewDto(
    MonitoringTemplateDetailsDto details,
    MonitoringTemplateDiffSummaryDto diff,
    boolean duplicateTemplateId
) {
}
