package com.networkscanner.backend.monitoring.impl;

import java.util.HashMap;
import java.util.Map;
import org.springframework.data.domain.Sort;

/**
 * Sort field mapping and {@link Sort} construction for monitored host list queries.
 */
final class MonitoredHostSortSupport {

  static final String DEFAULT_HOST_SORT_FIELD = "ip";

  private static final Map<String, String> MONITORED_HOST_SORT_FIELDS = createMonitoredHostSortFields();

  private MonitoredHostSortSupport() {
  }

  static Sort buildHostSort(String sortField, String sortOrder) {
    String requestedField = sortField == null || sortField.isBlank() ? DEFAULT_HOST_SORT_FIELD : sortField;
    String property = MONITORED_HOST_SORT_FIELDS.getOrDefault(requestedField, DEFAULT_HOST_SORT_FIELD);
    Sort.Direction direction = "desc".equalsIgnoreCase(sortOrder) ? Sort.Direction.DESC : Sort.Direction.ASC;
    Sort sort = Sort.by(direction, property);
    if (!"ip".equals(property)) {
      sort = sort.and(Sort.by(Sort.Direction.ASC, "ip"));
    }
    return sort;
  }

  private static Map<String, String> createMonitoredHostSortFields() {
    Map<String, String> fields = new HashMap<>();
    fields.put("name", "name");
    fields.put("hostName", "hostName");
    fields.put("ip", "ip");
    fields.put("macAddress", "macAddress");
    fields.put("model", "model");
    fields.put("status", "pollingStatus");
    fields.put("pollingStatus", "pollingStatus");
    fields.put("healthStatus", "healthStatus");
    fields.put("availability", "status");
    fields.put("group", "groupName");
    return Map.copyOf(fields);
  }
}
