package com.networkscanner.backend.monitoring.dto;

import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record EvaluatedMonitoringEvent(
    String messageId,
    String schemaVersion,
    Long deviceId,
    String deviceIp,
    String templateId,
    String templateVersion,
    String packVersion,
    OffsetDateTime collectedAt,
    List<ZabbixItemValue> values,
    List<MonitoringEventMutation> eventMutations,
    DeviceHealthStatus healthStatus
) {
}
