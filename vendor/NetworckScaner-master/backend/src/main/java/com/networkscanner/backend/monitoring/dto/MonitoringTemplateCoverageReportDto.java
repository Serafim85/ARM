package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MonitoringTemplateCoverageReportDto(
    List<MonitoringTemplateFeatureSupportDto> features,
    List<String> warnings,
    List<String> blockingErrors
) {
}
