package com.networkscanner.backend.workstation.impl;

import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;

final class ArmMonitoringNotificationSupport {

  private ArmMonitoringNotificationSupport() {
  }

  static MonitoringEventMutation toMutation(
      MonitoringEventEntity event,
      MonitoringEventMutationAction action
  ) {
    return new MonitoringEventMutation(
        action,
        event.getMetricName(),
        event.getTriggerUuid(),
        event.getTriggerName(),
        event.getTriggerExpression(),
        event.getRecoveryExpression(),
        event.getRecoveryPath(),
        event.getInstanceKey(),
        event.getThresholdLevel(),
        event.getThresholdValue(),
        event.getActualValue(),
        event.getBreachStartedAt(),
        event.getNormalizedAt(),
        event.getSeverity()
    );
  }

  static MonitoringEventMutationAction actionFor(MonitoringEventEntity event) {
    if (event.getStatus() == MonitoringEventStatus.RESOLVED) {
      return MonitoringEventMutationAction.RESOLVE;
    }
    return MonitoringEventMutationAction.OPEN;
  }
}
