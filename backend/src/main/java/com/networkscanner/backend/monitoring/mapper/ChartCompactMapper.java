package com.networkscanner.backend.monitoring.mapper;

import com.networkscanner.backend.monitoring.dto.ChartSeriesDto;
import com.networkscanner.backend.monitoring.dto.CompactChartPanelDto;
import com.networkscanner.backend.monitoring.dto.CompactMetricsBatchSeriesDto;
import com.networkscanner.backend.monitoring.dto.CompactMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.DeviceMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.MetricChartPanelDto;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchSeriesDto;
import com.networkscanner.backend.monitoring.dto.ValueMapSeriesMeta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Маппинг богатых {@link MetricValueDto}-точек в компактный формат серий (t[]/v[]/sv[])
 * для эндпоинтов SPA. Метаданные (подпись, единицы) выносятся один раз на ряд.
 */
public final class ChartCompactMapper {

  private ChartCompactMapper() {
  }

  public static CompactMetricsHistoryResponseDto toCompactResponse(DeviceMetricsHistoryResponseDto rich) {
    return toCompactResponse(rich, Map.of());
  }

  public static CompactMetricsHistoryResponseDto toCompactResponse(
      DeviceMetricsHistoryResponseDto rich,
      Map<String, ValueMapSeriesMeta> metaByMetric
  ) {
    if (rich == null) {
      return new CompactMetricsHistoryResponseDto(List.of(), 0);
    }
    Map<String, ValueMapSeriesMeta> meta = metaByMetric == null ? Map.of() : metaByMetric;
    List<CompactChartPanelDto> panels = new ArrayList<>();
    for (MetricChartPanelDto panel : rich.chartPanels()) {
      panels.add(toCompactPanel(panel, meta));
    }
    return new CompactMetricsHistoryResponseDto(panels, rich.totalChartPanels());
  }

  public static List<CompactMetricsBatchSeriesDto> toCompactBatch(List<MonitoringMetricsBatchSeriesDto> rich) {
    return toCompactBatch(rich, ignored -> null);
  }

  public static List<CompactMetricsBatchSeriesDto> toCompactBatch(
      List<MonitoringMetricsBatchSeriesDto> rich,
      Function<MonitoringMetricsBatchSeriesDto, ValueMapSeriesMeta> metaResolver
  ) {
    if (rich == null || rich.isEmpty()) {
      return List.of();
    }
    Function<MonitoringMetricsBatchSeriesDto, ValueMapSeriesMeta> resolve =
        metaResolver == null ? ignored -> null : metaResolver;
    List<CompactMetricsBatchSeriesDto> out = new ArrayList<>(rich.size());
    for (MonitoringMetricsBatchSeriesDto series : rich) {
      SeriesArrays arrays = buildArrays(series.points());
      ValueMapSeriesMeta meta = resolve.apply(series);
      out.add(new CompactMetricsBatchSeriesDto(
          series.deviceId(),
          series.metricName(),
          arrays.displayName(),
          arrays.unit(),
          arrays.scaledUnit(),
          arrays.t(),
          arrays.v(),
          arrays.sv(),
          meta == null ? null : meta.valueMapName(),
          meta == null ? null : meta.mappings()
      ));
    }
    return out;
  }

  private static CompactChartPanelDto toCompactPanel(
      MetricChartPanelDto panel,
      Map<String, ValueMapSeriesMeta> metaByMetric
  ) {
    Map<String, List<MetricValueDto>> byMetric = new LinkedHashMap<>();
    for (String name : orderedMetricNames(panel)) {
      byMetric.put(name, new ArrayList<>());
    }
    if (panel.points() != null) {
      for (MetricValueDto point : panel.points()) {
        String name = point.metricName();
        if (name == null) {
          continue;
        }
        byMetric.computeIfAbsent(name, ignored -> new ArrayList<>()).add(point);
      }
    }

    List<ChartSeriesDto> series = new ArrayList<>();
    for (Map.Entry<String, List<MetricValueDto>> entry : byMetric.entrySet()) {
      List<MetricValueDto> points = entry.getValue();
      if (points.isEmpty()) {
        continue;
      }
      SeriesArrays arrays = buildArrays(points);
      ValueMapSeriesMeta meta = metaByMetric.get(entry.getKey());
      series.add(new ChartSeriesDto(
          entry.getKey(),
          arrays.displayName(),
          arrays.unit(),
          arrays.scaledUnit(),
          arrays.t(),
          arrays.v(),
          arrays.sv(),
          meta == null ? null : meta.valueMapName(),
          meta == null ? null : meta.mappings()
      ));
    }

    return new CompactChartPanelDto(
        panel.panelKey(),
        panel.title(),
        panel.graphType(),
        panel.metricNames(),
        panel.rightAxisMetricNames(),
        series,
        panel.thresholds() == null ? List.of() : panel.thresholds()
    );
  }

  private static List<String> orderedMetricNames(MetricChartPanelDto panel) {
    LinkedHashSet<String> ordered = new LinkedHashSet<>();
    if (panel.metricNames() != null) {
      for (String name : panel.metricNames()) {
        if (name != null && !name.isBlank()) {
          ordered.add(name);
        }
      }
    }
    if (panel.rightAxisMetricNames() != null) {
      for (String name : panel.rightAxisMetricNames()) {
        if (name != null && !name.isBlank()) {
          ordered.add(name);
        }
      }
    }
    return new ArrayList<>(ordered);
  }

  private static SeriesArrays buildArrays(List<MetricValueDto> points) {
    if (points == null || points.isEmpty()) {
      return new SeriesArrays(null, null, null, new long[0], new double[0], null);
    }
    List<MetricValueDto> sorted = new ArrayList<>(points);
    sorted.sort(Comparator.comparing(
        MetricValueDto::recordedAt,
        Comparator.nullsLast(Comparator.naturalOrder())
    ));

    int size = sorted.size();
    long[] t = new long[size];
    double[] v = new double[size];
    double[] sv = new double[size];
    boolean hasScaled = false;
    String displayName = null;
    String unit = null;
    String scaledUnit = null;

    for (int i = 0; i < size; i++) {
      MetricValueDto point = sorted.get(i);
      t[i] = point.recordedAt() == null ? 0L : point.recordedAt().toInstant().toEpochMilli();
      v[i] = point.metricValue();
      if (point.scaledMetricValue() != null) {
        sv[i] = point.scaledMetricValue();
        hasScaled = true;
      } else {
        sv[i] = point.metricValue();
      }
      if (displayName == null && point.metricDisplayName() != null && !point.metricDisplayName().isBlank()) {
        displayName = point.metricDisplayName();
      }
      if (unit == null && point.unit() != null && !point.unit().isBlank()) {
        unit = point.unit();
      }
      if (scaledUnit == null && point.scaledUnit() != null && !point.scaledUnit().isBlank()) {
        scaledUnit = point.scaledUnit();
      }
    }

    return new SeriesArrays(displayName, unit, scaledUnit, t, v, hasScaled ? sv : null);
  }

  private record SeriesArrays(
      String displayName,
      String unit,
      String scaledUnit,
      long[] t,
      double[] v,
      double[] sv
  ) {
  }
}
