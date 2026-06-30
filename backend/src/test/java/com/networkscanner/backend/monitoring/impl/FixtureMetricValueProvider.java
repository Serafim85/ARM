package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Supplies metric history for {@link TriggerEvaluationSupport#evaluateExpression} from baseline + scenario overrides.
 */
public final class FixtureMetricValueProvider implements TriggerEvaluationSupport.MetricWindowValueProvider {

  private final Map<String, List<Double>> seriesByMetric;

  public FixtureMetricValueProvider(Map<String, List<Double>> seriesByMetric) {
    this.seriesByMetric = seriesByMetric == null ? Map.of() : Map.copyOf(seriesByMetric);
  }

  public static FixtureMetricValueProvider fromBaseline(List<ZabbixItemValue> baseline) {
    Map<String, List<Double>> series = new LinkedHashMap<>();
    for (ZabbixItemValue value : baseline) {
      if (value.numericValue() != null) {
        series.put(value.itemKey(), List.of(value.numericValue()));
      } else if (value.textValue() != null && !value.textValue().isBlank()) {
        try {
          series.put(value.itemKey(), List.of(Double.parseDouble(value.textValue().trim())));
        } catch (NumberFormatException ignored) {
          // non-numeric text metrics are not used in numeric trigger evaluation
        }
      }
    }
    return new FixtureMetricValueProvider(series);
  }

  public static FixtureMetricValueProvider fromBaselineWithScenario(
      List<ZabbixItemValue> baseline,
      JsonNode scenario
  ) {
    Map<String, List<Double>> series = new LinkedHashMap<>(fromBaseline(baseline).seriesByMetric);
    applyOverrides(series, scenario, "metricOverrides");
    applyOverrides(series, scenario, "historyOverrides");
    applyOverrides(series, scenario, "syntheticValues");
    return new FixtureMetricValueProvider(series);
  }

  private static void applyOverrides(Map<String, List<Double>> series, JsonNode scenario, String field) {
    if (scenario == null || !scenario.has(field)) {
      return;
    }
    JsonNode overrides = scenario.get(field);
    overrides.fields().forEachRemaining(entry -> {
      List<Double> values = new ArrayList<>();
      for (JsonNode node : entry.getValue()) {
        values.add(node.asDouble());
      }
      if (!values.isEmpty()) {
        series.put(entry.getKey(), List.copyOf(values));
      }
    });
  }

  @Override
  public List<Double> loadMetricValues(String metricName, String window, OffsetDateTime timestamp) {
    List<Double> values = seriesByMetric.get(metricName);
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    return values;
  }
}
