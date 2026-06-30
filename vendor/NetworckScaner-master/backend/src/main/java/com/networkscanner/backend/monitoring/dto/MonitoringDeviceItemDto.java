package com.networkscanner.backend.monitoring.dto;

public record MonitoringDeviceItemDto(
    String itemUuid,
    String itemKey,
    String name,
    String itemType,
    boolean discoveryPrototype,
    String discoveryRuleKey,
    String instanceKey,
    boolean active
) {
}
