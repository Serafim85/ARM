package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Результат SNMP-опроса: значения для истории и сырой ответ агента по metric_name (включая {@code []}).
 */
public record SnmpPollBatch(List<ZabbixItemValue> values, Map<String, String> rawByMetricName) {

  public SnmpPollBatch {
    values = values == null ? List.of() : List.copyOf(values);
    rawByMetricName = rawByMetricName == null ? Map.of() : safeRawByMetricName(rawByMetricName);
  }

  private Map<String, String> safeRawByMetricName(final Map<String, String> sourceMap) {
    HashMap<String, String> map = new HashMap<>();
    for (String s : sourceMap.keySet()) {
      String value = sourceMap.get(s);
      if( value != null) {
        map.put(s, value);
      }
    }
    return map;
  }
}
