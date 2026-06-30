package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MonitoringTemplateDetailsDto(
    MonitoringTemplateSummaryDto summary,
    MonitoringTemplateCoverageReportDto coverage,
    List<MonitoringTemplateItemDto> items,
    List<MonitoringTemplateDiscoveryRuleDto> discoveryRules,
    List<MonitoringTemplateTriggerDto> triggers,
    List<MonitoringTemplateValueMapDto> valueMaps,
    List<String> graphNames
) {
}
