package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateDiscoveryRuleDto(
    String key,
    String name,
    String type,
    int delaySeconds,
    int lifetimeSeconds,
    boolean hasFilter,
    int itemPrototypeCount,
    int triggerPrototypeCount,
    int graphPrototypeCount
) {
}
