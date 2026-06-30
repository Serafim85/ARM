package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger;
import com.networkscanner.backend.monitoring.dto.MetricChartThresholdDto;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ValueMapSeriesMeta;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.util.ValueMapSeriesResolver;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Рассчитывает числовые пороги триггеров для графиков и таблицы текущего состояния.
 */
final class MetricChartThresholdBuilder {

  record ChartThresholdBuildContext(
      OffsetDateTime from,
      OffsetDateTime to,
      Map<String, List<MetricValueDto>> pointsByMetric
  ) {
  }

  private final ZabbixRuntimeStateService runtimeStateService;
  private final UnitScalingService unitScalingService;

  MetricChartThresholdBuilder(
      ZabbixRuntimeStateService runtimeStateService,
      UnitScalingService unitScalingService
  ) {
    this.runtimeStateService = runtimeStateService;
    this.unitScalingService = unitScalingService;
  }

  List<MetricChartThresholdDto> build(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,
      OffsetDateTime timestamp,
      Map<String, String> unitByMetric,
      Map<String, UnitScalingService.SeriesScale> seriesScaleByMetric
  ) {
    return build(
        device,
        template,
        discoveryInstances,
        timestamp,
        unitByMetric,
        seriesScaleByMetric,
        null
    );
  }

  List<MetricChartThresholdDto> build(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,
      OffsetDateTime timestamp,
      Map<String, String> unitByMetric,
      Map<String, UnitScalingService.SeriesScale> seriesScaleByMetric,
      ChartThresholdBuildContext chartContext
  ) {
    if (template == null) {
      return List.of();
    }
    List<MaterializedZabbixTrigger> triggers =
        TriggerEvaluationSupport.materializeTriggers(template, discoveryInstances == null ? Map.of() : discoveryInstances);
    if (triggers.isEmpty()) {
      return List.of();
    }
    TriggerEvaluationSupport.MetricWindowValueProvider valueProvider = chartContext == null
        ? createValueProvider(device, triggers, timestamp)
        : createRangedValueProvider(device, triggers, chartContext.from(), chartContext.to());
    List<MetricChartThresholdDto> result = new ArrayList<>();
    Set<String> dedupe = new LinkedHashSet<>();
    for (MaterializedZabbixTrigger trigger : triggers) {
      String level = TriggerEvaluationSupport.mapThresholdLevel(trigger.runtime().priority()).name();
      String triggerName = blankToNull(trigger.runtime().name());
      String triggerUuid = blankToNull(trigger.runtime().uuid());
      for (TriggerEvaluationSupport.ChartThresholdComparison comparison
          : TriggerEvaluationSupport.extractChartThresholdComparisons(
          trigger.expression(),
          timestamp,
          valueProvider
      )) {
        String metricName = comparison.metricName();
        String instanceKey = TriggerEvaluationSupport.blankToEmpty(trigger.instanceKey());
        String dedupeKey = metricName
            + ":" + instanceKey
            + ":" + level
            + ":" + comparison.operator()
            + ":" + comparison.dynamic()
            + ":" + comparison.snapshotThresholdValue();
        if (!dedupe.add(dedupeKey)) {
          continue;
        }
        String unit = unitByMetric == null ? null : unitByMetric.get(metricName);
        UnitScalingService.SeriesScale scale =
            seriesScaleByMetric == null ? null : seriesScaleByMetric.get(metricName);
        double snapshotValue = comparison.snapshotThresholdValue();
        Double scaledThreshold = scaleThreshold(snapshotValue, unit, scale);
        List<Long> seriesT = null;
        List<Double> seriesV = null;
        List<Double> seriesSv = null;
        boolean dynamic = comparison.dynamic();
        if (dynamic && chartContext != null) {
          List<OffsetDateTime> timeline = timelineForMetric(chartContext.pointsByMetric(), metricName);
          List<TriggerEvaluationSupport.ThresholdSample> samples = TriggerEvaluationSupport.evaluateThresholdSeries(
              comparison,
              timeline,
              valueProvider
          );
          if (!samples.isEmpty()) {
            seriesT = new ArrayList<>(samples.size());
            seriesV = new ArrayList<>(samples.size());
            seriesSv = new ArrayList<>(samples.size());
            for (TriggerEvaluationSupport.ThresholdSample sample : samples) {
              seriesT.add(sample.recordedAt().toInstant().toEpochMilli());
              seriesV.add(sample.value());
              seriesSv.add(scaleThreshold(sample.value(), unit, scale));
            }
            TriggerEvaluationSupport.ThresholdSample last = samples.get(samples.size() - 1);
            snapshotValue = last.value();
            scaledThreshold = scaleThreshold(snapshotValue, unit, scale);
          }
        }
        ValueMapSeriesMeta valueMapMeta = ValueMapSeriesResolver.resolve(template, metricName);
        Map<String, String> valueMapMappings = valueMapMeta == null ? null : valueMapMeta.mappings();
        result.add(new MetricChartThresholdDto(
            metricName,
            instanceKey,
            triggerName,
            triggerUuid,
            level,
            snapshotValue,
            scaledThreshold,
            comparison.operator(),
            dynamic && seriesT != null && !seriesT.isEmpty(),
            seriesT,
            seriesV,
            seriesSv,
            valueMapMappings
        ));
      }
    }
    return List.copyOf(result);
  }

  Map<String, List<MetricChartThresholdDto>> indexByMetricInstance(List<MetricChartThresholdDto> thresholds) {
    if (thresholds == null || thresholds.isEmpty()) {
      return Map.of();
    }
    Map<String, List<MetricChartThresholdDto>> indexed = new LinkedHashMap<>();
    for (MetricChartThresholdDto threshold : thresholds) {
      String key = TriggerEvaluationSupport.metricInstanceKey(threshold.metricName(), threshold.instanceKey());
      indexed.computeIfAbsent(key, ignored -> new ArrayList<>()).add(threshold);
    }
    for (Map.Entry<String, List<MetricChartThresholdDto>> entry : indexed.entrySet()) {
      entry.setValue(List.copyOf(entry.getValue()));
    }
    return Map.copyOf(indexed);
  }

  static List<MetricChartThresholdDto> forPanel(
      List<MetricChartThresholdDto> allThresholds,
      Set<String> panelMetricNames
  ) {
    if (allThresholds == null || allThresholds.isEmpty() || panelMetricNames == null || panelMetricNames.isEmpty()) {
      return List.of();
    }
    return allThresholds.stream()
        .filter(threshold -> threshold.metricName() != null && panelMetricNames.contains(threshold.metricName()))
        .toList();
  }

  private static List<OffsetDateTime> timelineForMetric(
      Map<String, List<MetricValueDto>> pointsByMetric,
      String metricName
  ) {
    if (pointsByMetric == null || metricName == null) {
      return List.of();
    }
    return pointsByMetric.getOrDefault(metricName, List.of()).stream()
        .map(MetricValueDto::recordedAt)
        .filter(Objects::nonNull)
        .distinct()
        .sorted()
        .toList();
  }

  private Double scaleThreshold(
      double thresholdValue,
      String unit,
      UnitScalingService.SeriesScale seriesScale
  ) {
    if (!Double.isFinite(thresholdValue)) {
      return null;
    }
    UnitScalingService.ScalingResult scaled = unitScalingService.applySeriesScale(thresholdValue, unit, seriesScale);
    return scaled.scaledValue();
  }

  private TriggerEvaluationSupport.MetricWindowValueProvider createValueProvider(
      MonitoredDeviceEntity device,
      List<MaterializedZabbixTrigger> triggers,
      OffsetDateTime timestamp
  ) {
    List<ItemStateSnapshot> itemState = runtimeStateService.loadItemStateList(device);
    Map<String, String> latestTextByMetric = itemState.stream()
        .filter(snapshot -> snapshot.itemKey() != null && snapshot.textValue() != null)
        .collect(Collectors.toMap(
            ItemStateSnapshot::itemKey,
            ItemStateSnapshot::textValue,
            (left, right) -> right,
            LinkedHashMap::new
        ));
    List<MetricHistoryRequest> requests =
        TriggerEvaluationSupport.collectHistoryRequestsForMaterialized(triggers, timestamp);
    Map<MetricHistoryRequest, List<MetricHistoryPoint>> history = requests.isEmpty()
        ? Map.of()
        : runtimeStateService.loadMetricHistoryBatch(device, requests);
    return new TriggerEvaluationSupport.MetricWindowValueProvider() {
      @Override
      public List<Double> loadMetricValues(String metricName, String window, OffsetDateTime evaluationTimestamp) {
        MetricHistoryRequest key = TriggerEvaluationSupport.toHistoryRequest(
            metricName,
            window,
            evaluationTimestamp == null ? timestamp : evaluationTimestamp
        );
        List<MetricHistoryPoint> points = history.getOrDefault(key, List.of());
        if (points.isEmpty()) {
          return runtimeStateService.loadRecentNumericValues(device, metricName, null, null, 1);
        }
        return points.stream().map(MetricHistoryPoint::value).toList();
      }

      @Override
      public String loadLatestTextValue(String metricName, OffsetDateTime evaluationTimestamp) {
        return latestTextByMetric.get(metricName);
      }
    };
  }

  private TriggerEvaluationSupport.MetricWindowValueProvider createRangedValueProvider(
      MonitoredDeviceEntity device,
      List<MaterializedZabbixTrigger> triggers,
      OffsetDateTime from,
      OffsetDateTime to
  ) {
    List<ItemStateSnapshot> itemState = runtimeStateService.loadItemStateList(device);
    Map<String, String> latestTextByMetric = itemState.stream()
        .filter(snapshot -> snapshot.itemKey() != null && snapshot.textValue() != null)
        .collect(Collectors.toMap(
            ItemStateSnapshot::itemKey,
            ItemStateSnapshot::textValue,
            (left, right) -> right,
            LinkedHashMap::new
        ));
    LinkedHashSet<String> metricNames = new LinkedHashSet<>();
    for (MaterializedZabbixTrigger trigger : triggers) {
      metricNames.addAll(TriggerEvaluationSupport.historyMetricNamesForExpression(trigger.expression()));
    }
    OffsetDateTime since = from == null ? to : from;
    List<MetricHistoryRequest> requests = metricNames.stream()
        .map(metricName -> new MetricHistoryRequest(metricName, since, null))
        .toList();
    Map<String, List<MetricHistoryPoint>> historyByMetric = new LinkedHashMap<>();
    if (!requests.isEmpty()) {
      Map<MetricHistoryRequest, List<MetricHistoryPoint>> loaded =
          runtimeStateService.loadMetricHistoryBatch(device, requests);
      for (Map.Entry<MetricHistoryRequest, List<MetricHistoryPoint>> entry : loaded.entrySet()) {
        historyByMetric.put(entry.getKey().metricName(), entry.getValue());
      }
    }
    return new RangedHistoryValueProvider(device, runtimeStateService, historyByMetric, latestTextByMetric);
  }

  private static final class RangedHistoryValueProvider implements TriggerEvaluationSupport.MetricWindowValueProvider {
    private final MonitoredDeviceEntity device;
    private final ZabbixRuntimeStateService runtimeStateService;
    private final Map<String, List<MetricHistoryPoint>> historyByMetric;
    private final Map<String, String> latestTextByMetric;

    private RangedHistoryValueProvider(
        MonitoredDeviceEntity device,
        ZabbixRuntimeStateService runtimeStateService,
        Map<String, List<MetricHistoryPoint>> historyByMetric,
        Map<String, String> latestTextByMetric
    ) {
      this.device = device;
      this.runtimeStateService = runtimeStateService;
      this.historyByMetric = historyByMetric == null ? Map.of() : historyByMetric;
      this.latestTextByMetric = latestTextByMetric == null ? Map.of() : latestTextByMetric;
    }

    @Override
    public List<Double> loadMetricValues(String metricName, String window, OffsetDateTime evaluationTimestamp) {
      OffsetDateTime evalAt = evaluationTimestamp == null ? OffsetDateTime.now() : evaluationTimestamp;
      List<MetricHistoryPoint> filtered = filterPointsForWindow(
          historyByMetric.getOrDefault(metricName, List.of()),
          window,
          evalAt
      );
      if (filtered.isEmpty()) {
        return runtimeStateService.loadRecentNumericValues(device, metricName, null, null, 1);
      }
      return filtered.stream().map(MetricHistoryPoint::value).toList();
    }

    @Override
    public String loadLatestTextValue(String metricName, OffsetDateTime evaluationTimestamp) {
      return latestTextByMetric.get(metricName);
    }

    private static List<MetricHistoryPoint> filterPointsForWindow(
        List<MetricHistoryPoint> points,
        String window,
        OffsetDateTime evaluationTimestamp
    ) {
      List<MetricHistoryPoint> eligible = points.stream()
          .filter(point -> point.recordedAt() != null && !point.recordedAt().isAfter(evaluationTimestamp))
          .sorted(Comparator.comparing(MetricHistoryPoint::recordedAt).reversed())
          .toList();
      if (eligible.isEmpty()) {
        return List.of();
      }
      if (window == null || window.isBlank()) {
        return List.of(eligible.get(0));
      }
      String trimmed = window.trim().toLowerCase(Locale.ROOT);
      if (trimmed.startsWith("#")) {
        try {
          int count = Integer.parseInt(trimmed.substring(1));
          return eligible.stream().limit(Math.max(0, count)).toList();
        } catch (NumberFormatException ignored) {
          return List.of(eligible.get(0));
        }
      }
      Long seconds = TriggerEvaluationSupport.parseWindowSeconds(trimmed);
      if (seconds != null) {
        OffsetDateTime since = evaluationTimestamp.minusSeconds(seconds);
        return eligible.stream()
            .filter(point -> !point.recordedAt().isBefore(since))
            .toList();
      }
      return List.of(eligible.get(0));
    }
  }

  private static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }
}
