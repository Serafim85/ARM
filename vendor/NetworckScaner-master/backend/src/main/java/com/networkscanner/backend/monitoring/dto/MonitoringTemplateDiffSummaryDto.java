package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateDiffSummaryDto(
    boolean replacingExistingTemplate,
    int itemDelta,
    int discoveryRuleDelta,
    int triggerDelta,
    int valueMapDelta,
    int graphDelta
) {
}
