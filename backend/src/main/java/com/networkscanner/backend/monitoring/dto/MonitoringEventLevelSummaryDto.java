package com.networkscanner.backend.monitoring.dto;

/**
 * Агрегаты по уровню порога (Zabbix severity, {@link com.networkscanner.backend.monitoring.model.ThresholdLevel})
 * для текущих фильтров списка событий.
 */
public record MonitoringEventLevelSummaryDto(
    long disaster,
    long high,
    long average,
    long warning,
    long information,
    long notClassified
) {
}
