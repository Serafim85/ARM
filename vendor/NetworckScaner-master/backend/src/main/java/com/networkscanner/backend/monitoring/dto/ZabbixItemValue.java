package com.networkscanner.backend.monitoring.dto;

public record ZabbixItemValue(
    String templateId,
    String metricName,
    String itemKey,
    String instanceKey,
    String discoveryRuleKey,
    String itemUuid,
    Double numericValue,
    String textValue,
    String unitLabel,
    String valueMapName,
    String preprocessingStatus,
    String preprocessingNote
) {
}
