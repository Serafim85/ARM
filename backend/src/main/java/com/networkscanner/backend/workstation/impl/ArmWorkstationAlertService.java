package com.networkscanner.backend.workstation.impl;

import com.networkscanner.backend.agentingest.dto.AgentMetricPointDto;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.api.ThresholdEvaluationService;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import com.networkscanner.backend.workstation.model.WorkstationEntity;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name = "app.workstation.alerts.enabled", havingValue = "true", matchIfMissing = true)
public class ArmWorkstationAlertService {

  private static final Logger log = LoggerFactory.getLogger(ArmWorkstationAlertService.class);

  private final WorkstationMonitoredDeviceBridge deviceBridge;
  private final MonitoringTemplateResolver templateResolver;
  private final ThresholdEvaluationService thresholdEvaluationService;
  private final MonitoringEventRepository eventRepository;
  private final NotificationDispatchService notificationDispatchService;

  public ArmWorkstationAlertService(
      WorkstationMonitoredDeviceBridge deviceBridge,
      MonitoringTemplateResolver templateResolver,
      ThresholdEvaluationService thresholdEvaluationService,
      MonitoringEventRepository eventRepository,
      NotificationDispatchService notificationDispatchService
  ) {
    this.deviceBridge = deviceBridge;
    this.templateResolver = templateResolver;
    this.thresholdEvaluationService = thresholdEvaluationService;
    this.eventRepository = eventRepository;
    this.notificationDispatchService = notificationDispatchService;
  }

  @Transactional
  public void evaluateAfterIngest(
      WorkstationEntity workstation,
      List<AgentMetricPointDto> metrics,
      OffsetDateTime timestamp
  ) {
    if (workstation == null || metrics == null || metrics.isEmpty()) {
      return;
    }
    try {
      MonitoredDeviceEntity device = deviceBridge.getOrCreate(workstation);
      String templateId = ArmWorkstationTemplateSupport.templateIdForOsType(workstation.getOsType());
      ResolvedMonitoringTemplate template = templateResolver.resolveTemplateById(templateId);
      Map<String, ItemStateSnapshot> itemState = buildItemState(templateId, metrics, timestamp);
      Set<Long> openBefore = openEventIds(device.getId());
      thresholdEvaluationService.evaluateTriggers(
          device,
          template,
          itemState,
          Map.of(),
          timestamp
      );
      dispatchChangedEvents(device, workstation, openBefore, timestamp);
    } catch (Exception ex) {
      log.warn("ARM alert evaluation failed for workstation {}: {}", workstation.getHostname(), ex.getMessage());
    }
  }

  @Transactional
  public void notifyConnectivityChange(
      WorkstationEntity workstation,
      MonitoringEventMutationAction action,
      String details
  ) {
    MonitoredDeviceEntity device = deviceBridge.getOrCreate(workstation);
    MonitoringEventMutation mutation = new MonitoringEventMutation(
        action,
        "arm.agent.heartbeat",
        null,
        action == MonitoringEventMutationAction.RESOLVE
            ? "ARM: workstation back online"
            : "ARM: workstation offline",
        null,
        null,
        null,
        null,
        null,
        0.0,
        0.0,
        OffsetDateTime.now(),
        action == MonitoringEventMutationAction.RESOLVE ? OffsetDateTime.now() : null,
        action == MonitoringEventMutationAction.RESOLVE ? "INFORMATION" : "HIGH"
    );
    dispatch(device, workstation, mutation, details);
  }

  private Map<String, ItemStateSnapshot> buildItemState(
      String templateId,
      List<AgentMetricPointDto> metrics,
      OffsetDateTime timestamp
  ) {
    Map<String, ItemStateSnapshot> itemState = new HashMap<>();
    for (AgentMetricPointDto metric : metrics) {
      if (metric == null || metric.key() == null || metric.key().isBlank()) {
        continue;
      }
      OffsetDateTime collectedAt = metric.clock() != null ? metric.clock() : timestamp;
      itemState.put(
          metric.key(),
          new ItemStateSnapshot(
              templateId,
              metric.key(),
              null,
              metric.value(),
              null,
              null,
              null,
              null,
              null,
              collectedAt
          )
      );
    }
    return itemState;
  }

  private Set<Long> openEventIds(Long deviceId) {
    return eventRepository.findByDevice_IdAndStatus(deviceId, MonitoringEventStatus.OPEN).stream()
        .map(MonitoringEventEntity::getId)
        .collect(Collectors.toCollection(HashSet::new));
  }

  private void dispatchChangedEvents(
      MonitoredDeviceEntity device,
      WorkstationEntity workstation,
      Set<Long> openBefore,
      OffsetDateTime evaluationTime
  ) {
    OffsetDateTime resolveWindowStart = evaluationTime.minusSeconds(5);
    List<MonitoringEventEntity> events = eventRepository.findByDevice_IdOrderByBreachStartedAtDesc(device.getId());
    for (MonitoringEventEntity event : events) {
      MonitoringEventMutationAction action = null;
      if (event.getStatus() == MonitoringEventStatus.OPEN && !openBefore.contains(event.getId())) {
        action = MonitoringEventMutationAction.OPEN;
      } else if (event.getStatus() == MonitoringEventStatus.RESOLVED
          && event.getNormalizedAt() != null
          && !event.getNormalizedAt().isBefore(resolveWindowStart)) {
        action = MonitoringEventMutationAction.RESOLVE;
      }
      if (action == null) {
        continue;
      }
      dispatch(device, workstation, ArmMonitoringNotificationSupport.toMutation(event, action), null);
    }
  }

  private void dispatch(
      MonitoredDeviceEntity device,
      WorkstationEntity workstation,
      MonitoringEventMutation mutation,
      String extraDetails
  ) {
    List<String> tags = List.of(ArmWorkstationTemplateSupport.TAG_ARM_WORKSTATION);
    notificationDispatchService.notifyMonitoringEvent(
        device.getId(),
        device.getIp(),
        workstation.getHostname(),
        tags,
        mutation
    );
    if (extraDetails != null && !extraDetails.isBlank()) {
      log.info("ARM connectivity alert for {}: {}", workstation.getHostname(), extraDetails);
    }
  }
}
