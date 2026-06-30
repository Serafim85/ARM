package com.networkscanner.backend.integration.dto;

import java.time.Instant;

public record ExternalIncidentUpsert(
    String schemaVersion,
    String eventId,
    String sourceSystem,
    Long externalDeviceId,
    String templateId,
    String templateVersion,
    IncidentStatus incidentStatus,
    String triggerUuid,
    String triggerName,
    String metricName,
    String instanceKey,
    String thresholdLevel,
    double thresholdValue,
    double actualValue,
    Instant breachStartedAt,
    Instant normalizedAt,
    Instant receivedAt,
    String correlationKey,
    String severity,
    String triggerExpression,
    String recoveryExpression,
    String recoveryPath,
    String packVersion,
    String metricUnit,
    String metricDisplayName
) {
}
