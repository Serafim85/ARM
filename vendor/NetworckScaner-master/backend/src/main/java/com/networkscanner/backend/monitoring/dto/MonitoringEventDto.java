package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;

public record MonitoringEventDto(
    Long id,
    Long deviceId,
    String deviceIp,
    String deviceName,
    /** Имя хоста из SNMP (sysName), поле host_name устройства. */
    String deviceHostName,
    String deviceMacAddress,
    String templateId,
    String metricName,
    /** Human-readable name from monitoring template item; {@code null} if unknown. */
    String metricDisplayName,
    String triggerName,
    String triggerExpression,
    String recoveryExpression,
    String recoveryPath,
    String thresholdLevel,
    double thresholdValue,
    double actualValue,
    OffsetDateTime breachStartedAt,
    OffsetDateTime normalizedAt,
    String status
) {
}
