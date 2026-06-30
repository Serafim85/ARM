package com.networkscanner.backend.monitoring.dto;

public record MonitoringTemplateItemDto(
    String key,
    String name,
    String type,
    String valueType,
    String units,
    int delaySeconds,
    String snmpOid,
    String masterItemKey,
    String params,
    String preprocessing,
    String valueMapName,
    boolean discoveryPrototype,
    String discoveryRuleKey,
    boolean runtimeSupported
) {
}
