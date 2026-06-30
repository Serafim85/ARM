package com.networkscanner.backend.monitoring.dto;

/**
 * Дополнительный контекст для препроцессинга (например SNMP_WALK_VALUE по JSON мастер-item).
 */
public record MonitoringPreprocessContext(
    ResolvedMonitoringTemplate template,
    MaterializedZabbixItem materializedItem
) {
  public static final MonitoringPreprocessContext NONE = new MonitoringPreprocessContext(null, null);
}
