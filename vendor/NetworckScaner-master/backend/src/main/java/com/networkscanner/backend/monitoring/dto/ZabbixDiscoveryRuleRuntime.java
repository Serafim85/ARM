package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record ZabbixDiscoveryRuleRuntime(
    String uuid,
    String key,
    String name,
    String type,
    String snmpOid,
    String masterItemKey,
    List<ZabbixPreprocessingStep> preprocessing,
    List<ZabbixLldMacroPathRecord> lldMacroPaths,
    int delaySeconds,
    int lifetimeSeconds,
    ZabbixDiscoveryFilterRecord filter,
    List<ZabbixItemRuntime> itemPrototypes,
    List<ZabbixTriggerRuntime> triggerPrototypes,
    List<ZabbixGraphRecord> graphPrototypes
) {
  public boolean isDependent() {
    return "DEPENDENT".equalsIgnoreCase(type);
  }
}
