package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record ZabbixTriggerRuntime(
    String uuid,
    String name,
    String expression,
    String recoveryMode,
    String recoveryExpression,
    List<String> dependencyKeys,
    List<ZabbixTagRecord> tags,
    boolean manualClose,
    String priority,
    boolean discoveryPrototype,
    String discoveryRuleKey
) {
}
