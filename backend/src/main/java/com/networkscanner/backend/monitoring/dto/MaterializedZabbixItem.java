package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

public record MaterializedZabbixItem(
    String templateId,
    ZabbixItemRuntime runtime,
    String key,
    String metricName,
    String instanceKey,
    String discoveryRuleKey,
    String snmpOid,
    Map<String, String> macros
) {
}
