package com.networkscanner.backend.monitoring.dto;

import java.util.Map;
import java.util.Set;

public record MaterializedZabbixTrigger(
    ZabbixTriggerRuntime runtime,
    String triggerKey,
    String instanceKey,
    String expression,
    String recoveryExpression,
    Set<String> dependencyKeys,
    Map<String, String> macros
) {
}
