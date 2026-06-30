package com.networkscanner.backend.monitoring.model;

/**
 * Severity триггера Zabbix (поле {@code priority} в YAML-экспорте), хранится в {@code monitoring_events.threshold_level}.
 */
public enum ThresholdLevel {
  NOT_CLASSIFIED,
  INFORMATION,
  WARNING,
  AVERAGE,
  HIGH,
  DISASTER
}
