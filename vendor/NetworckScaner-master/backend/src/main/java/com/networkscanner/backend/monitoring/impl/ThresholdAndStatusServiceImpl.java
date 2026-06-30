package com.networkscanner.backend.monitoring.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.api.ThresholdAndStatusService;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ThresholdAndStatusServiceImpl implements ThresholdAndStatusService {

  private static final int MAX_HISTORY_POINTS = 256;
  private static final Logger log = LoggerFactory.getLogger(ThresholdAndStatusServiceImpl.class);

  private final MonitoringTemplateResolver templateResolver;
  private final ZabbixRuntimeStateService stateService;
  private final MonitoringEventRepository eventRepository;
  private final MonitoringPipelineMessageStore messageStore;
  private final Cache<Long, DeviceContext> deviceContexts;
  private final Cache<TriggerPlanKey, TriggerPlan> triggerPlans;
  private final Cache<TemplateResolutionKey, ResolvedMonitoringTemplate> resolvedTemplates;
  private final LongAdder triggerPlanCacheHits = new LongAdder();
  private final LongAdder triggerPlanCacheMisses = new LongAdder();

  public ThresholdAndStatusServiceImpl(
      MonitoringTemplateResolver templateResolver,
      ZabbixRuntimeStateService stateService,
      MonitoringEventRepository eventRepository,
      MonitoringPipelineMessageStore messageStore,
      MonitoringKafkaProperties properties
  ) {
    this.templateResolver = templateResolver;
    this.stateService = stateService;
    this.eventRepository = eventRepository;
    this.messageStore = messageStore;
    this.deviceContexts = Caffeine.newBuilder()
        .maximumSize(Math.max(1, properties.getCache().getMaxDevices()))
        .expireAfterAccess(Duration.ofMinutes(Math.max(1, properties.getCache().getExpireAfterMinutes())))
        .build();
    this.resolvedTemplates = Caffeine.newBuilder()
        .maximumSize(Math.max(256, properties.getCache().getMaxDevices() * 2L))
        .expireAfterAccess(Duration.ofMinutes(Math.max(1, properties.getCache().getExpireAfterMinutes())))
        .build();
    this.triggerPlans = Caffeine.newBuilder()
        .maximumSize(Math.max(256, properties.getCache().getMaxDevices() * 4L))
        .expireAfterAccess(Duration.ofMinutes(Math.max(1, properties.getCache().getExpireAfterMinutes())))
        .build();
  }

  @Override
  public EvaluatedMonitoringEvent evaluate(PolledMetricsEvent event) {
    if (!messageStore.markProcessed(event.messageId(), "EVALUATOR")) {
      return null;
    }

    List<String> templateIds = event.values().stream()
        .map(com.networkscanner.backend.monitoring.dto.ZabbixItemValue::templateId)
        .filter(id -> id != null && !id.isBlank())
        .distinct()
        .toList();
    ResolvedMonitoringTemplate template = resolveEventTemplate(event, templateIds);
    DeviceContext context = deviceContexts.get(event.deviceId(), ignored -> initializeContext(event, template));
    String discoverySignature = discoverySignature(event.discoveryInstances());
    if (!context.matchesTemplate(template.id(), template.templateVersion(), discoverySignature)) {
      context = initializeContext(event, template);
      deviceContexts.put(event.deviceId(), context);
    }

    applyIncomingValues(context, event.values(), event.collectedAt());
    refreshOpenEventsFromDatabase(event.deviceId(), context);
    List<MonitoringEventMutation> mutations = evaluateMutations(
        template,
        event.discoveryInstances(),
        context,
        event.collectedAt()
    );
    context.healthStatus = deriveHealthStatus(context.openEventsIndex);

    return new EvaluatedMonitoringEvent(
        event.messageId(),
        event.schemaVersion(),
        event.deviceId(),
        event.deviceIp(),
        template.id(),
        template.templateVersion(),
        template.packVersion(),
        event.collectedAt(),
        List.copyOf(event.values()),
        List.copyOf(mutations),
        context.healthStatus
    );
  }

  private DeviceContext initializeContext(PolledMetricsEvent event, ResolvedMonitoringTemplate template) {
    MonitoredDeviceEntity device = deviceStub(event.deviceId(), event.deviceIp());
    String discoverySignature = discoverySignature(event.discoveryInstances());
    List<com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger> materializedTriggers =
        resolveTriggerPlan(template, discoverySignature, event.discoveryInstances()).triggers();
    Map<String, OpenEventState> openEventsIndex = loadOpenEventsIndex(event.deviceId());
    Map<String, List<MetricHistoryPoint>> history = loadMetricHistory(
        device,
        materializedTriggers,
        event.collectedAt()
    );
    return new DeviceContext(
        history,
        new HashMap<>(),
        openEventsIndex,
        deriveHealthStatus(openEventsIndex),
        template.id(),
        template.templateVersion(),
        discoverySignature
    );
  }

  private void refreshOpenEventsFromDatabase(Long deviceId, DeviceContext context) {
    context.openEventsIndex.clear();
    context.openEventsIndex.putAll(loadOpenEventsIndex(deviceId));
  }

  private Map<String, OpenEventState> loadOpenEventsIndex(Long deviceId) {
    List<MonitoringEventEntity> openEvents =
        eventRepository.findByDevice_IdAndStatus(deviceId, MonitoringEventStatus.OPEN);
    Map<String, OpenEventState> index = new HashMap<>(openEvents.size() * 2);
    for (MonitoringEventEntity event : openEvents) {
      index.put(
          TriggerEvaluationSupport.indexKey(event.getTriggerUuid(), event.getInstanceKey(), event.getThresholdLevel()),
          new OpenEventState(
              event.getTemplateId(),
              event.getTemplateVersion(),
              event.getPackVersion(),
              event.getMetricName(),
              event.getTriggerUuid(),
              event.getTriggerName(),
              event.getTriggerExpression(),
              event.getRecoveryExpression(),
              event.getRecoveryPath(),
              TriggerEvaluationSupport.blankToEmpty(event.getInstanceKey()),
              event.getThresholdLevel(),
              event.getThresholdValue(),
              event.getActualValue(),
              event.getBreachStartedAt(),
              event.getSeverity()
          )
      );
    }
    return index;
  }

  private Map<String, List<MetricHistoryPoint>> loadMetricHistory(
      MonitoredDeviceEntity device,
      List<com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger> materializedTriggers,
      OffsetDateTime timestamp
  ) {
    List<MetricHistoryRequest> requests =
        TriggerEvaluationSupport.collectHistoryRequestsForMaterialized(materializedTriggers, timestamp);
    if (requests.isEmpty()) {
      return new HashMap<>();
    }
    Map<MetricHistoryRequest, List<MetricHistoryPoint>> loaded = stateService.loadMetricHistoryBatch(device, requests);
    Map<String, List<MetricHistoryPoint>> history = new HashMap<>();
    for (Map.Entry<MetricHistoryRequest, List<MetricHistoryPoint>> entry : loaded.entrySet()) {
      mergeHistory(history, entry.getKey().metricName(), entry.getValue());
    }
    return history;
  }

  private void applyIncomingValues(DeviceContext context, List<ZabbixItemValue> values, OffsetDateTime timestamp) {
    for (ZabbixItemValue value : values) {
      if (value.numericValue() != null) {
        mergeHistory(
            context.metricHistory,
            value.metricName(),
            List.of(new MetricHistoryPoint(timestamp, value.numericValue()))
        );
      }
      if (value.textValue() != null && !value.textValue().isBlank()) {
        context.metricText.put(value.metricName(), value.textValue());
      }
    }
  }

  private List<MonitoringEventMutation> evaluateMutations(
      ResolvedMonitoringTemplate template,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,
      DeviceContext context,
      OffsetDateTime timestamp
  ) {
    List<MonitoringEventMutation> mutations = new ArrayList<>();
    Set<String> activeTriggerKeys = collectActiveTriggerKeys(context.openEventsIndex);
    String discoverySignature = discoverySignature(discoveryInstances);
    List<com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger> materializedTriggers =
        resolveTriggerPlan(template, discoverySignature, discoveryInstances).triggers();
    TriggerEvaluationSupport.MetricWindowValueProvider valueProvider =
        new TriggerEvaluationSupport.MetricWindowValueProvider() {
          @Override
          public List<Double> loadMetricValues(String metricName, String window, OffsetDateTime evaluationTimestamp) {
            return ThresholdAndStatusServiceImpl.this.loadMetricValues(context, metricName, window, evaluationTimestamp);
          }

          @Override
          public String loadLatestTextValue(String metricName, OffsetDateTime evaluationTimestamp) {
            return context.metricText.get(metricName);
          }
        };
    for (var trigger : materializedTriggers) {
      TriggerEvaluationSupport.TriggerEvaluation breachEvaluation =
          TriggerEvaluationSupport.evaluateExpression(trigger.expression(), timestamp, valueProvider);
      if (breachEvaluation == null) {
        continue;
      }
      ThresholdLevel level = TriggerEvaluationSupport.mapThresholdLevel(trigger.runtime().priority());
      String indexKey = TriggerEvaluationSupport.indexKey(trigger.triggerKey(), trigger.instanceKey(), level);
      OpenEventState existingEvent = context.openEventsIndex.get(indexKey);
      if (existingEvent == null && breachEvaluation.breached()) {
        existingEvent = findOpenEventByMetric(context, breachEvaluation.metricName(), trigger.instanceKey());
      }
      if (breachEvaluation.breached() && existingEvent == null && isDependencyBlocked(trigger, activeTriggerKeys)) {
        continue;
      }

      if (breachEvaluation.breached() && existingEvent == null) {
        OpenEventState created = new OpenEventState(
            template.id(),
            template.templateVersion(),
            template.packVersion(),
            breachEvaluation.metricName(),
            trigger.runtime().uuid(),
            trigger.runtime().name(),
            trigger.expression(),
            trigger.recoveryExpression(),
            null,
            TriggerEvaluationSupport.blankToEmpty(trigger.instanceKey()),
            level,
            breachEvaluation.thresholdValue(),
            breachEvaluation.actualValue(),
            timestamp,
            trigger.runtime().priority()
        );
        context.openEventsIndex.put(indexKey, created);
        registerActiveTrigger(activeTriggerKeys, trigger.runtime().uuid(), trigger.runtime().name(), trigger.expression());
        mutations.add(toMutation(MonitoringEventMutationAction.OPEN, created, timestamp));
      } else if (breachEvaluation.breached()) {
        OpenEventState updated = existingEvent.withActualValue(
            breachEvaluation.actualValue(),
            breachEvaluation.thresholdValue(),
            trigger.runtime().priority(),
            trigger.expression(),
            trigger.recoveryExpression(),
            null
        );
        String storeKey = openEventIndexKey(existingEvent, indexKey);
        context.openEventsIndex.put(storeKey, updated);
        if (!storeKey.equals(indexKey)) {
          context.openEventsIndex.remove(indexKey);
        }
        mutations.add(toMutation(MonitoringEventMutationAction.UPDATE, updated, null));
      } else if (existingEvent != null) {
        RecoveryDecision recoveryDecision = evaluateRecovery(
            trigger,
            breachEvaluation.actualValue(),
            timestamp,
            valueProvider
        );
        if (!recoveryDecision.canClose()) {
          continue;
        }
        OpenEventState resolved = existingEvent.withActualValue(
            existingEvent.actualValue,
            existingEvent.thresholdValue,
            existingEvent.severity,
            existingEvent.triggerExpression,
            existingEvent.recoveryExpression,
            recoveryDecision.path()
        );
        String storeKey = openEventIndexKey(existingEvent, indexKey);
        context.openEventsIndex.remove(storeKey);
        if (!storeKey.equals(indexKey)) {
          context.openEventsIndex.remove(indexKey);
        }
        unregisterActiveTrigger(activeTriggerKeys, trigger.runtime().uuid(), trigger.runtime().name(), trigger.expression());
        mutations.add(toMutation(MonitoringEventMutationAction.RESOLVE, resolved, timestamp));
      }
    }
    return mutations;
  }

  private static String openEventIndexKey(OpenEventState event, String fallbackIndexKey) {
    if (event == null) {
      return fallbackIndexKey;
    }
    return TriggerEvaluationSupport.indexKey(event.triggerUuid, event.instanceKey, event.thresholdLevel);
  }

  private OpenEventState findOpenEventByMetric(
      DeviceContext context,
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
    for (OpenEventState state : context.openEventsIndex.values()) {
      if (metricKey.equals(TriggerEvaluationSupport.metricInstanceKey(state.metricName, state.instanceKey))) {
        return state;
      }
    }
    return null;
  }

  private MonitoringEventMutation toMutation(
      MonitoringEventMutationAction action,
      OpenEventState event,
      OffsetDateTime normalizedAt
  ) {
    return new MonitoringEventMutation(
        action,
        event.metricName,
        event.triggerUuid,
        event.triggerName,
        event.triggerExpression,
        event.recoveryExpression,
        event.recoveryPath,
        TriggerEvaluationSupport.blankToNull(event.instanceKey),
        event.thresholdLevel,
        event.thresholdValue,
        event.actualValue,
        event.breachStartedAt,
        normalizedAt,
        event.severity
    );
  }

  private DeviceHealthStatus deriveHealthStatus(Map<String, OpenEventState> openEventsIndex) {
    return TriggerEvaluationSupport.deriveHealthStatus(
        openEventsIndex.values().stream().map(OpenEventState::thresholdLevel).toList()
    );
  }

  private boolean isDependencyBlocked(
      com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger trigger,
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
      com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger trigger,
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

  private Set<String> collectActiveTriggerKeys(Map<String, OpenEventState> openEventsIndex) {
    Set<String> keys = new HashSet<>();
    for (OpenEventState event : openEventsIndex.values()) {
      registerActiveTrigger(keys, event.triggerUuid, event.triggerName, event.triggerExpression);
    }
    return keys;
  }

  private void registerActiveTrigger(Set<String> target, String triggerUuid, String triggerName, String triggerExpression) {
    if (triggerUuid != null && !triggerUuid.isBlank()) {
      target.add(triggerUuid);
    }
    if (triggerName != null && !triggerName.isBlank()) {
      target.add(triggerName);
    }
    if (triggerExpression != null && !triggerExpression.isBlank()) {
      target.add(triggerExpression);
    }
  }

  private void unregisterActiveTrigger(Set<String> target, String triggerUuid, String triggerName, String triggerExpression) {
    if (triggerUuid != null && !triggerUuid.isBlank()) {
      target.remove(triggerUuid);
    }
    if (triggerName != null && !triggerName.isBlank()) {
      target.remove(triggerName);
    }
    if (triggerExpression != null && !triggerExpression.isBlank()) {
      target.remove(triggerExpression);
    }
  }

  private List<Double> loadMetricValues(
      DeviceContext context,
      String metricName,
      String window,
      OffsetDateTime timestamp
  ) {
    List<MetricHistoryPoint> points = context.metricHistory.getOrDefault(metricName, List.of());
    if (points.isEmpty()) {
      return List.of();
    }
    if (window == null || window.isBlank()) {
      return List.of(points.get(0).value());
    }
    String trimmed = window.trim().toLowerCase();
    if (trimmed.startsWith("#")) {
      int limit;
      try {
        limit = Integer.parseInt(trimmed.substring(1));
      } catch (NumberFormatException exception) {
        log.warn("Invalid count window '{}' for metric '{}'", window, metricName);
        return List.of(points.get(0).value());
      }
      return points.stream()
          .limit(limit)
          .map(MetricHistoryPoint::value)
          .toList();
    }
    Long seconds = TriggerEvaluationSupport.parseWindowSeconds(trimmed);
    if (seconds != null) {
      OffsetDateTime since = timestamp.minusSeconds(seconds);
      return points.stream()
          .filter(point -> !point.recordedAt().isBefore(since))
          .map(MetricHistoryPoint::value)
          .toList();
    }
    return List.of(points.get(0).value());
  }

  private void mergeHistory(
      Map<String, List<MetricHistoryPoint>> history,
      String metricName,
      List<MetricHistoryPoint> points
  ) {
    if (metricName == null || points == null || points.isEmpty()) {
      return;
    }
    Map<String, MetricHistoryPoint> merged = new LinkedHashMap<>();
    for (MetricHistoryPoint point : history.getOrDefault(metricName, List.of())) {
      merged.put(point.recordedAt() + "|" + point.value(), point);
    }
    for (MetricHistoryPoint point : points) {
      merged.put(point.recordedAt() + "|" + point.value(), point);
    }
    List<MetricHistoryPoint> sorted = merged.values().stream()
        .sorted(Comparator.comparing(MetricHistoryPoint::recordedAt).reversed())
        .limit(MAX_HISTORY_POINTS)
        .toList();
    history.put(metricName, new ArrayList<>(sorted));
  }

  private MonitoredDeviceEntity deviceStub(Long deviceId, String deviceIp) {
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(deviceId);
    device.setIp(deviceIp);
    return device;
  }

  private static class DeviceContext {
    private final Map<String, List<MetricHistoryPoint>> metricHistory;
    private final Map<String, String> metricText;
    private final Map<String, OpenEventState> openEventsIndex;
    private DeviceHealthStatus healthStatus;
    private final String templateId;
    private final String templateVersion;
    private final String discoverySignature;

    private DeviceContext(
        Map<String, List<MetricHistoryPoint>> metricHistory,
        Map<String, String> metricText,
        Map<String, OpenEventState> openEventsIndex,
        DeviceHealthStatus healthStatus,
        String templateId,
        String templateVersion,
        String discoverySignature
    ) {
      this.metricHistory = metricHistory;
      this.metricText = metricText;
      this.openEventsIndex = openEventsIndex;
      this.healthStatus = healthStatus;
      this.templateId = templateId;
      this.templateVersion = templateVersion;
      this.discoverySignature = discoverySignature;
    }

    private boolean matchesTemplate(
        String nextTemplateId,
        String nextTemplateVersion,
        String nextDiscoverySignature
    ) {
      return java.util.Objects.equals(templateId, nextTemplateId)
          && java.util.Objects.equals(templateVersion, nextTemplateVersion)
          && java.util.Objects.equals(discoverySignature, nextDiscoverySignature);
    }
  }

  private record OpenEventState(
      String templateId,
      String templateVersion,
      String packVersion,
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
      String severity
  ) {
    private OpenEventState withActualValue(
        double newActualValue,
        double newThresholdValue,
        String newSeverity,
        String newTriggerExpression,
        String newRecoveryExpression,
        String newRecoveryPath
    ) {
      return new OpenEventState(
          templateId,
          templateVersion,
          packVersion,
          metricName,
          triggerUuid,
          triggerName,
          newTriggerExpression,
          newRecoveryExpression,
          newRecoveryPath,
          instanceKey,
          thresholdLevel,
          newThresholdValue,
          newActualValue,
          breachStartedAt,
          newSeverity
      );
    }
  }

  private record RecoveryDecision(
      boolean canClose,
      String path,
      double actualValue
  ) {
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

  private TriggerPlan resolveTriggerPlan(
      ResolvedMonitoringTemplate template,
      String discoverySignature,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances
  ) {
    TriggerPlanKey key = new TriggerPlanKey(
        template.id(),
        template.templateVersion(),
        discoverySignature
    );
    TriggerPlan cached = triggerPlans.getIfPresent(key);
    if (cached != null) {
      triggerPlanCacheHits.increment();
      maybeLogTriggerPlanCacheStats();
      return cached;
    }
    triggerPlanCacheMisses.increment();
    TriggerPlan built = new TriggerPlan(
        TriggerEvaluationSupport.materializeTriggers(template, discoveryInstances)
    );
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
      log.debug("Trigger plan cache stats: hits={}, misses={}, hitRatio={}%", hits, misses, String.format("%.2f", hitRatio));
    }
  }

  private record TriggerPlanKey(
      String templateId,
      String templateVersion,
      String discoverySignature
  ) {
  }

  private record TriggerPlan(
      List<com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger> triggers
  ) {
  }

  private ResolvedMonitoringTemplate resolveEventTemplate(PolledMetricsEvent event, List<String> templateIds) {
    TemplateResolutionKey key = new TemplateResolutionKey(
        templateIds == null ? List.of() : List.copyOf(templateIds),
        event.templateId(),
        event.vendor(),
        event.model()
    );
    return resolvedTemplates.get(
        key,
        ignored -> templateIds == null || templateIds.isEmpty()
            ? templateResolver.resolveForDevice(event.templateId(), event.vendor(), event.model())
            : templateResolver.resolveMergedTemplates(templateIds)
    );
  }

  private record TemplateResolutionKey(
      List<String> templateIds,
      String templateId,
      String vendor,
      String model
  ) {
  }
}
