package com.networkscanner.backend.monitoring.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.event.WislaIncidentChangedEvent;
import com.networkscanner.backend.integration.impl.ExternalIncidentUpsertMapper;
import com.networkscanner.backend.monitoring.api.ThresholdEvaluationService;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ThresholdEvaluationServiceImpl implements ThresholdEvaluationService {

  private static final Logger log = LoggerFactory.getLogger(ThresholdEvaluationServiceImpl.class);

  private final MonitoringEventRepository eventRepository;
  private final ZabbixRuntimeStateService stateService;
  private final Cache<TriggerPlanKey, TriggerPlan> triggerPlans;
  private final LongAdder triggerPlanCacheHits = new LongAdder();
  private final LongAdder triggerPlanCacheMisses = new LongAdder();
  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;
  @Autowired
  private ExternalIncidentUpsertMapper externalIncidentUpsertMapper;
  @Autowired(required = false)
  private SourceSystemProvider sourceSystemProvider;

  public ThresholdEvaluationServiceImpl(
      MonitoringEventRepository eventRepository,
      ZabbixRuntimeStateService stateService
  ) {
    this.eventRepository = eventRepository;
    this.stateService = stateService;
    this.triggerPlans = Caffeine.newBuilder()
        .maximumSize(8192)
        .expireAfterAccess(30, TimeUnit.MINUTES)
        .build();
  }

  @Override
  @Transactional
  public void evaluateTriggers(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      Map<String, ItemStateSnapshot> itemState,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,
      OffsetDateTime timestamp
  ) {
    if ((template.triggers() == null || template.triggers().isEmpty())
        && (template.discoveryRules() == null || template.discoveryRules().isEmpty())) {
      return;
    }

    Map<String, MonitoringEventEntity> openEventsIndex = loadOpenEventsIndex(device.getId());
    Set<String> activeTriggerKeys = collectActiveTriggerKeys(openEventsIndex);
    List<MonitoringEventEntity> changedEntities = new ArrayList<>();
    String discoverySignature = discoverySignature(discoveryInstances);
    List<MaterializedZabbixTrigger> materializedTriggers =
        resolveTriggerPlan(template, discoverySignature, discoveryInstances).triggers();
    TriggerEvaluationSupport.MetricWindowValueProvider valueProvider =
        createHistoryProvider(device, itemState, materializedTriggers, timestamp);

    for (MaterializedZabbixTrigger trigger : materializedTriggers) {
      TriggerEvaluationSupport.TriggerEvaluation breachEvaluation =
          TriggerEvaluationSupport.evaluateExpression(trigger.expression(), timestamp, valueProvider);
      if (breachEvaluation == null) {
        continue;
      }
      ThresholdLevel level = TriggerEvaluationSupport.mapThresholdLevel(trigger.runtime().priority());
      String indexKey = TriggerEvaluationSupport.indexKey(trigger.triggerKey(), trigger.instanceKey(), level);
      MonitoringEventEntity existingEvent = openEventsIndex.get(indexKey);
      if (existingEvent == null && breachEvaluation.breached()) {
        existingEvent = findOpenEventByMetric(openEventsIndex, breachEvaluation.metricName(), trigger.instanceKey());
      }
      if (breachEvaluation.breached() && existingEvent == null && isDependencyBlocked(trigger, activeTriggerKeys)) {
        log.debug(
            "Skip opening event for trigger {} due to active dependency",
            trigger.runtime().name()
        );
        continue;
      }

      if (breachEvaluation.breached() && existingEvent == null) {
        MonitoringEventEntity event = new MonitoringEventEntity();
        event.setDevice(device);
        event.setTemplateId(template.id());
        event.setTemplateVersion(template.templateVersion());
        event.setPackVersion(template.packVersion());
        event.setMetricName(breachEvaluation.metricName());
        event.setTriggerUuid(trigger.runtime().uuid());
        event.setTriggerName(trigger.runtime().name());
        event.setTriggerExpression(trigger.expression());
        event.setRecoveryExpression(trigger.recoveryExpression());
        event.setInstanceKey(TriggerEvaluationSupport.blankToNull(trigger.instanceKey()));
        event.setThresholdLevel(level);
        event.setThresholdValue(breachEvaluation.thresholdValue());
        event.setActualValue(breachEvaluation.actualValue());
        event.setBreachStartedAt(timestamp);
        event.setStatus(MonitoringEventStatus.OPEN);
        event.setSeverity(trigger.runtime().priority());
        event.setRecoveryPath(null);
        activeTriggerKeys.add(trigger.triggerKey());
        if (trigger.runtime().uuid() != null) {
          activeTriggerKeys.add(trigger.runtime().uuid());
        }
        activeTriggerKeys.add(trigger.expression());
        changedEntities.add(event);
      } else if (breachEvaluation.breached()) {
        existingEvent.setActualValue(breachEvaluation.actualValue());
        existingEvent.setSeverity(trigger.runtime().priority());
        existingEvent.setTriggerExpression(trigger.expression());
        existingEvent.setRecoveryExpression(trigger.recoveryExpression());
        changedEntities.add(existingEvent);
      } else if (existingEvent != null) {
        RecoveryDecision recoveryDecision = evaluateRecovery(
            trigger,
            breachEvaluation.actualValue(),
            timestamp,
            valueProvider
        );
        if (recoveryDecision.canClose()) {
          existingEvent.setNormalizedAt(timestamp);
          existingEvent.setStatus(MonitoringEventStatus.RESOLVED);
          existingEvent.setRecoveryPath(recoveryDecision.path());
          activeTriggerKeys.remove(trigger.triggerKey());
          if (trigger.runtime().uuid() != null) {
            activeTriggerKeys.remove(trigger.runtime().uuid());
          }
          activeTriggerKeys.remove(trigger.expression());
          changedEntities.add(existingEvent);
        }
      }
    }

    if (!changedEntities.isEmpty()) {
      eventRepository.saveAll(changedEntities);
      if (applicationEventPublisher != null && externalIncidentUpsertMapper != null) {
        String sourceSystemValue = sourceSystemProvider != null
            ? sourceSystemProvider.getSourceSystem()
            : "networkscanner";
        for (MonitoringEventEntity entity : changedEntities) {
          if (entity.getStatus() == MonitoringEventStatus.OPEN || entity.getStatus() == MonitoringEventStatus.RESOLVED) {
            applicationEventPublisher.publishEvent(
                new WislaIncidentChangedEvent(externalIncidentUpsertMapper.fromEntity(entity, sourceSystemValue))
            );
          }
        }
      }
      log.debug("Device {} [{}]: saved {} trigger event(s)",
          device.getIp(), device.getId(), changedEntities.size());
    }
  }

  private MonitoringEventEntity findOpenEventByMetric(
      Map<String, MonitoringEventEntity> openEventsIndex,
      String metricName,
      String instanceKey
  ) {
    if (metricName == null || metricName.isBlank()) {
      return null;
    }
    String metricKey = TriggerEvaluationSupport.metricInstanceKey(
        metricName,
        TriggerEvaluationSupport.blankToEmpty(instanceKey)
    );
    for (MonitoringEventEntity event : openEventsIndex.values()) {
      if (metricKey.equals(TriggerEvaluationSupport.metricInstanceKey(
          event.getMetricName(),
          TriggerEvaluationSupport.blankToEmpty(event.getInstanceKey())
      ))) {
        return event;
      }
    }
    return null;
  }

  private Map<String, MonitoringEventEntity> loadOpenEventsIndex(Long deviceId) {
    List<MonitoringEventEntity> openEvents =
        eventRepository.findByDevice_IdAndStatus(deviceId, MonitoringEventStatus.OPEN);
    Map<String, MonitoringEventEntity> index = new HashMap<>(openEvents.size() * 2);
    for (MonitoringEventEntity event : openEvents) {
      index.put(TriggerEvaluationSupport.indexKey(event.getTriggerUuid(), event.getInstanceKey(), event.getThresholdLevel()), event);
    }
    return index;
  }

  private boolean isDependencyBlocked(
      MaterializedZabbixTrigger trigger,
      Set<String> activeTriggerKeys
  ) {
    if (trigger.dependencyKeys() == null || trigger.dependencyKeys().isEmpty()) {
      return false;
    }
    for (String dependency : trigger.dependencyKeys()) {
      if (activeTriggerKeys.contains(dependency)) {
        return true;
      }
    }
    return false;
  }

  private RecoveryDecision evaluateRecovery(
      MaterializedZabbixTrigger trigger,
      double implicitActualValue,
      OffsetDateTime timestamp,
      TriggerEvaluationSupport.MetricWindowValueProvider valueProvider
  ) {
    if (trigger.recoveryExpression() == null || trigger.recoveryExpression().isBlank()) {
      return new RecoveryDecision(true, "implicit_inverse", implicitActualValue);
    }
    TriggerEvaluationSupport.TriggerEvaluation recoveryEvaluation =
        TriggerEvaluationSupport.evaluateExpression(trigger.recoveryExpression(), timestamp, valueProvider);
    if (recoveryEvaluation == null) {
      return new RecoveryDecision(false, "recovery_expression_wait", 0.0d);
    }
    return new RecoveryDecision(
        recoveryEvaluation.breached(),
        "recovery_expression",
        recoveryEvaluation.actualValue()
    );
  }

  private TriggerEvaluationSupport.MetricWindowValueProvider createHistoryProvider(
      MonitoredDeviceEntity device,
      Map<String, ItemStateSnapshot> itemState,
      List<MaterializedZabbixTrigger> materializedTriggers,
      OffsetDateTime timestamp
  ) {
    Map<String, String> latestTextByMetric = itemState == null ? Map.of() : itemState.values().stream()
        .filter(snapshot -> snapshot != null && snapshot.itemKey() != null && snapshot.textValue() != null)
        .collect(java.util.stream.Collectors.toMap(
            ItemStateSnapshot::itemKey,
            ItemStateSnapshot::textValue,
            (left, right) -> right,
            java.util.LinkedHashMap::new
        ));
    if (materializedTriggers == null || materializedTriggers.isEmpty()) {
      return new TriggerEvaluationSupport.MetricWindowValueProvider() {
        @Override
        public List<Double> loadMetricValues(String metricName, String window, OffsetDateTime evaluationTimestamp) {
          return stateService.loadRecentNumericValues(device, metricName, null, null, 1);
        }

        @Override
        public String loadLatestTextValue(String metricName, OffsetDateTime evaluationTimestamp) {
          return latestTextByMetric.get(metricName);
        }
      };
    }
    List<MetricHistoryRequest> requests =
        TriggerEvaluationSupport.collectHistoryRequestsForMaterialized(materializedTriggers, timestamp);
    Map<MetricHistoryRequest, List<MetricHistoryPoint>> history =
        requests.isEmpty() ? Map.of() : stateService.loadMetricHistoryBatch(device, requests);
    return new TriggerEvaluationSupport.MetricWindowValueProvider() {
      @Override
      public List<Double> loadMetricValues(String metricName, String window, OffsetDateTime evaluationTimestamp) {
        MetricHistoryRequest key = toHistoryRequest(metricName, window, evaluationTimestamp == null ? timestamp : evaluationTimestamp);
        List<MetricHistoryPoint> points = history.getOrDefault(key, List.of());
        if (points.isEmpty()) {
          return List.of();
        }
        return points.stream().map(MetricHistoryPoint::value).toList();
      }

      @Override
      public String loadLatestTextValue(String metricName, OffsetDateTime evaluationTimestamp) {
        return latestTextByMetric.get(metricName);
      }
    };
  }

  private MetricHistoryRequest toHistoryRequest(String metricName, String window, OffsetDateTime timestamp) {
    return TriggerEvaluationSupport.toHistoryRequest(metricName, window, timestamp);
  }

  private record RecoveryDecision(
      boolean canClose,
      String path,
      double actualValue
  ) {
  }

  private Set<String> collectActiveTriggerKeys(Map<String, MonitoringEventEntity> openEventsIndex) {
    Set<String> keys = new HashSet<>();
    for (MonitoringEventEntity openEvent : openEventsIndex.values()) {
      if (openEvent.getTriggerUuid() != null && !openEvent.getTriggerUuid().isBlank()) {
        keys.add(openEvent.getTriggerUuid());
      }
      if (openEvent.getTriggerName() != null && !openEvent.getTriggerName().isBlank()) {
        keys.add(openEvent.getTriggerName());
      }
      if (openEvent.getTriggerExpression() != null && !openEvent.getTriggerExpression().isBlank()) {
        keys.add(openEvent.getTriggerExpression());
      }
    }
    return keys;
  }

  private TriggerPlan resolveTriggerPlan(
      ResolvedMonitoringTemplate template,
      String discoverySignature,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances
  ) {
    TriggerPlanKey key = new TriggerPlanKey(template.id(), template.templateVersion(), discoverySignature);
    TriggerPlan cached = triggerPlans.getIfPresent(key);
    if (cached != null) {
      triggerPlanCacheHits.increment();
      maybeLogTriggerPlanCacheStats();
      return cached;
    }
    triggerPlanCacheMisses.increment();
    TriggerPlan built = new TriggerPlan(TriggerEvaluationSupport.materializeTriggers(template, discoveryInstances));
    triggerPlans.put(key, built);
    maybeLogTriggerPlanCacheStats();
    return built;
  }

  private void maybeLogTriggerPlanCacheStats() {
    long hits = triggerPlanCacheHits.sum();
    long misses = triggerPlanCacheMisses.sum();
    long total = hits + misses;
    if (total > 0 && total % 1000 == 0) {
      double hitRatio = (hits * 100.0d) / total;
      log.debug("Sync trigger plan cache stats: hits={}, misses={}, hitRatio={}%", hits, misses, String.format("%.2f", hitRatio));
    }
  }

  private String discoverySignature(Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances) {
    if (discoveryInstances == null || discoveryInstances.isEmpty()) {
      return "";
    }
    return discoveryInstances.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> {
          String instances = entry.getValue().stream()
              .map(DiscoveryInstanceRuntime::instanceKey)
              .sorted()
              .collect(java.util.stream.Collectors.joining(","));
          return entry.getKey() + ":" + instances;
        })
        .collect(java.util.stream.Collectors.joining("|"));
  }

  private record TriggerPlanKey(
      String templateId,
      String templateVersion,
      String discoverySignature
  ) {
  }

  private record TriggerPlan(
      List<MaterializedZabbixTrigger> triggers
  ) {
  }
}
