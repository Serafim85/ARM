package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateTriggerDto(
    String uuid,
    String name,
    String expression,
    String priority,
    boolean discoveryPrototype,
    String discoveryRuleKey
) {
}
