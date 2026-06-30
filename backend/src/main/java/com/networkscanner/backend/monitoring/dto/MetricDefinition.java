package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

public record MetricDefinition(
    Object oid,
    String unit,
    Object warn,
    Object critical,
    String preprocessing,
    /** Zabbix item {@code name} from template YAML; for UI labels. */
    String itemDisplayName
) {

  public boolean isSingleOid() {
    return oid instanceof String;
  }

  public String singleOid() {
    return (String) oid;
  }

  @SuppressWarnings("unchecked")
  public Map<String, String> multiOid() {
    return (Map<String, String>) oid;
  }

  public Double warnThreshold() {
    return parseThreshold(warn);
  }

  public Double criticalThreshold() {
    return parseThreshold(critical);
  }

  private static Double parseThreshold(Object value) {
    if (value == null || "-".equals(value)) {
      return null;
    }
    if (value instanceof Number number) {
      return number.doubleValue();
    }
    return null;
  }
}
