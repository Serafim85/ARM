package com.networkscanner.backend.monitoring.dto;

import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import java.time.OffsetDateTime;

public record MonitoringEventMutation(
    MonitoringEventMutationAction action,
    String metricName,
    String triggerUuid,
    String triggerName,
    String triggerExpression,
    String recoveryExpression,
    String recoveryPath,
    String instanceKey,
    ThresholdLevel thresholdLevel,
    double thresholdValue,
    double actualValue,
    OffsetDateTime breachStartedAt,
    OffsetDateTime normalizedAt,
    String severity
) {
}
