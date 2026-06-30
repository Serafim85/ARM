package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

public record ResolvedMonitoringTemplate(
    String id,
    String type,
    String name,
    String description,
    String extendsTemplate,
    String vendor,
    String modelRegex,
    int priority,
    String schemaVersion,
    String packVersion,
    String templateVersion,
    MonitoringTemplateSnmp snmp,
    MonitoringTemplateOids oids,
    Map<String, UnitDefinition> units,
    Map<String, PreprocessingFunctionDefinition> preprocessingFunctions,
    Map<String, MetricDefinition> metrics,
    Map<String, String> itemTemplateIds,
    Map<String, ZabbixItemRuntime> items,
    Map<String, ZabbixDiscoveryRuleRuntime> discoveryRules,
    Map<String, ZabbixValueMapRuntime> valueMaps,
    Map<String, ZabbixTriggerRuntime> triggers,
    java.util.List<ZabbixGraphRecord> graphs,
    Map<String, String> templateMacros,
    MonitoringTemplateCoverageReportDto coverage,
    boolean uiVisible
) {
  public String oid(String group, String key) {
    Map<String, String> values = switch (group) {
      case "discovery" -> oids.discovery();
      case "details" -> oids.details();
      case "interfaces" -> oids.interfaces();
      default -> Map.of();
    };
    return values.get(key);
  }

  public ZabbixItemRuntime item(String key) {
    return items == null ? null : items.get(key);
  }

  public ZabbixDiscoveryRuleRuntime discoveryRule(String key) {
    return discoveryRules == null ? null : discoveryRules.get(key);
  }
}
