package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.integration.api.InstanceKeyHostProvider;
import com.networkscanner.backend.integration.dto.ExternalIncidentUpsert;
import com.networkscanner.backend.integration.dto.IncidentStatus;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ExternalIncidentUpsertMapper {

  private final InstanceKeyHostProvider instanceKeyHostProvider;

  public ExternalIncidentUpsertMapper(InstanceKeyHostProvider instanceKeyHostProvider) {
    this.instanceKeyHostProvider = instanceKeyHostProvider;
  }

  public ExternalIncidentUpsert fromEntity(MonitoringEventEntity event, String sourceSystem) {
    String instanceKey = resolveInstanceKey();
    return new ExternalIncidentUpsert(
        "1.0",
        UUID.randomUUID().toString(),
        sourceSystem,
        event.getDevice().getId(),
        event.getTemplateId(),
        event.getTemplateVersion(),
        event.getStatus() == MonitoringEventStatus.RESOLVED ? IncidentStatus.RESOLVED : IncidentStatus.OPEN,
        event.getTriggerUuid(),
        event.getTriggerName(),
        event.getMetricName(),
        instanceKey,
        event.getThresholdLevel() == null ? null : event.getThresholdLevel().name(),
        event.getThresholdValue(),
        event.getActualValue(),
        toInstantOrNull(event.getBreachStartedAt()),
        toInstantOrNull(event.getNormalizedAt()),
        Instant.now(),
        correlationKey(
            event.getTriggerUuid(),
            instanceKey,
            event.getThresholdLevel() == null ? null : event.getThresholdLevel().name(),
            toInstantOrNull(event.getBreachStartedAt())
        ),
        event.getSeverity(),
        event.getTriggerExpression(),
        event.getRecoveryExpression(),
        event.getRecoveryPath(),
        event.getPackVersion(),
        "",
        event.getMetricName()
    );
  }

  public ExternalIncidentUpsert fromMutation(
      EvaluatedMonitoringEvent event,
      MonitoringEventMutation mutation,
      String sourceSystem
  ) {
    String instanceKey = resolveInstanceKey();
    Instant breachStarted = toInstantOrNull(mutation.breachStartedAt());
    return new ExternalIncidentUpsert(
        "1.0",
        UUID.randomUUID().toString(),
        sourceSystem,
        event.deviceId(),
        event.templateId(),
        event.templateVersion(),
        toIncidentStatus(mutation.action()),
        mutation.triggerUuid(),
        mutation.triggerName(),
        mutation.metricName(),
        instanceKey,
        mutation.thresholdLevel() == null ? null : mutation.thresholdLevel().name(),
        mutation.thresholdValue(),
        mutation.actualValue(),
        breachStarted,
        toInstantOrNull(mutation.normalizedAt()),
        toInstantOrNull(event.collectedAt()),
        correlationKey(
            mutation.triggerUuid(),
            instanceKey,
            mutation.thresholdLevel() == null ? null : mutation.thresholdLevel().name(),
            breachStarted
        ),
        mutation.severity(),
        mutation.triggerExpression(),
        mutation.recoveryExpression(),
        mutation.recoveryPath(),
        event.packVersion(),
        "",
        mutation.metricName()
    );
  }

  private IncidentStatus toIncidentStatus(MonitoringEventMutationAction action) {
    if (action == MonitoringEventMutationAction.RESOLVE) {
      return IncidentStatus.RESOLVED;
    }
    if (action == MonitoringEventMutationAction.UPDATE) {
      return IncidentStatus.UPDATE;
    }
    return IncidentStatus.OPEN;
  }

  private static Instant toInstantOrNull(OffsetDateTime odt) {
    return odt == null ? null : odt.toInstant();
  }

  private String resolveInstanceKey() {
    String host = instanceKeyHostProvider.getHostName();
    return host == null || host.isBlank() ? "networkscanner" : host;
  }

  private String correlationKey(
      String triggerUuid,
      String instanceKey,
      String thresholdLevel,
      Instant breachStartedAt
  ) {
    return (triggerUuid == null ? "" : triggerUuid) + "|"
        + (instanceKey == null ? "" : instanceKey) + "|"
        + (thresholdLevel == null ? "" : thresholdLevel) + "|"
        + (breachStartedAt == null ? "" : breachStartedAt.toString());
  }
}
