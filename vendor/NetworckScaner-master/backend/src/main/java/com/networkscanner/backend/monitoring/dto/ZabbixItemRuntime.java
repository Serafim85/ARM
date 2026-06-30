package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record ZabbixItemRuntime(
    String uuid,
    String key,
    String name,
    String type,
    String snmpOid,
    int delaySeconds,
    String valueType,
    String units,
    String params,
    String masterItemKey,
    String url,
    String description,
    List<ZabbixPreprocessingStep> preprocessing,
    String valueMapName,
    boolean discoveryPrototype,
    String discoveryRuleKey
) {
  public boolean isSnmpBased() {
    return "SNMP_AGENT".equalsIgnoreCase(type) || "SIMPLE".equalsIgnoreCase(type)
        || (type == null || type.isBlank());
  }

  public boolean isZabbixIcmpSimpleItem() {
    if (!"SIMPLE".equalsIgnoreCase(type)) {
      return false;
    }
    String baseKey = normalizedKeyBase();
    return "icmpping".equals(baseKey) || "icmppingloss".equals(baseKey) || "icmppingsec".equals(baseKey);
  }

  public boolean isDependent() {
    return "DEPENDENT".equalsIgnoreCase(type);
  }

  public boolean isCalculated() {
    return "CALCULATED".equalsIgnoreCase(type);
  }

  public boolean isTextual() {
    return "CHAR".equalsIgnoreCase(valueType) || "TEXT".equalsIgnoreCase(valueType)
        || "LOG".equalsIgnoreCase(valueType);
  }

  private String normalizedKeyBase() {
    if (key == null) {
      return "";
    }
    int bracketIndex = key.indexOf('[');
    String baseKey = bracketIndex >= 0 ? key.substring(0, bracketIndex) : key;
    return baseKey.trim().toLowerCase();
  }
}
