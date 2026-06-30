package com.networkscanner.backend.workstation.impl;

import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.workstation.model.WorkstationEntity;
import com.networkscanner.backend.workstation.repository.WorkstationRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.workstation.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class ArmWorkstationOfflineAlertScheduler {

  private final WorkstationRepository workstationRepository;
  private final ArmWorkstationAlertService alertService;
  private final int offlineThresholdMinutes;
  private final Map<Long, Boolean> offlineNotified = new ConcurrentHashMap<>();

  public ArmWorkstationOfflineAlertScheduler(
      WorkstationRepository workstationRepository,
      ArmWorkstationAlertService alertService,
      @Value("${app.workstation.offline-threshold-minutes:10}") int offlineThresholdMinutes
  ) {
    this.workstationRepository = workstationRepository;
    this.alertService = alertService;
    this.offlineThresholdMinutes = offlineThresholdMinutes;
  }

  @Scheduled(fixedDelayString = "${app.workstation.alerts.offline-check-interval-ms:120000}")
  public void checkOfflineWorkstations() {
    OffsetDateTime now = OffsetDateTime.now();
    for (WorkstationEntity workstation : workstationRepository.findAll()) {
      if (workstation.getId() == null) {
        continue;
      }
      String effectiveStatus = WorkstationStatusSupport.effectiveStatus(
          workstation,
          offlineThresholdMinutes,
          now
      );
      boolean wasNotifiedOffline = offlineNotified.getOrDefault(workstation.getId(), false);
      if (WorkstationStatusSupport.STATUS_OFFLINE.equals(effectiveStatus) && !wasNotifiedOffline) {
        alertService.notifyConnectivityChange(
            workstation,
            MonitoringEventMutationAction.OPEN,
            "No agent heartbeat for " + offlineThresholdMinutes + " minutes"
        );
        offlineNotified.put(workstation.getId(), true);
      } else if (WorkstationStatusSupport.STATUS_ONLINE.equals(effectiveStatus) && wasNotifiedOffline) {
        alertService.notifyConnectivityChange(
            workstation,
            MonitoringEventMutationAction.RESOLVE,
            "Agent heartbeat restored"
        );
        offlineNotified.put(workstation.getId(), false);
      }
    }
  }
}
