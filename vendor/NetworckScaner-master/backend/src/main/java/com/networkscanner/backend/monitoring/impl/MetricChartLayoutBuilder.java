package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.MetricChartPanelDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixGraphItemRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixGraphRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Builds metric chart panels from template graph metadata.
 *
 * <p>Rules:
 * 1) graph_prototypes are materialized per active discovery instance;
 * 2) static template graphs are appended after graph prototypes;
 * 3) first panel wins when one metric is referenced by multiple graphs;
 * 4) metrics not referenced by any graph are emitted as single-metric panels;
 * 5) PIE graphs are included ({@code GRAPH_SUM} items are still skipped — no numeric series).
 */
final class MetricChartLayoutBuilder {

  List<MetricChartPanelDto> build(
      ResolvedMonitoringTemplate template,
      Map<String, List<DiscoveryInstanceRuntime>> activeInstancesByRule,
      Set<String> metricNames,
      Map<String, String> metricDisplayNames
  ) {
    if (metricNames == null || metricNames.isEmpty()) {
      return List.of();
    }

    Set<String> knownMetrics = new LinkedHashSet<>();
    for (String metricName : metricNames) {
      if (metricName != null && !metricName.isBlank()) {
        knownMetrics.add(metricName);
      }
    }
    if (knownMetrics.isEmpty()) {
      return List.of();
    }

    List<MetricChartPanelDto> result = new ArrayList<>();
    Set<String> assigned = new LinkedHashSet<>();
    Map<String, List<DiscoveryInstanceRuntime>> active = activeInstancesByRule == null ? Map.of() : activeInstancesByRule;

    if (template != null && template.discoveryRules() != null) {
      for (ZabbixDiscoveryRuleRuntime rule : template.discoveryRules().values()) {
        List<DiscoveryInstanceRuntime> instances = active.getOrDefault(rule.key(), List.of());
        if (instances.isEmpty()) {
          instances = inferSyntheticDiscoveryInstances(rule, knownMetrics);
        }
        if (instances.isEmpty() || rule.graphPrototypes() == null || rule.graphPrototypes().isEmpty()) {
          continue;
        }
        int graphIndex = 0;
        for (ZabbixGraphRecord graph : rule.graphPrototypes()) {
          graphIndex++;
          for (DiscoveryInstanceRuntime instance : instances) {
            Map<String, String> macros = instance.macros() == null ? Map.of() : instance.macros();
            List<GraphSeriesRef> refs = materializeGraphItems(graph, macros, knownMetrics, assigned);
            if (refs.isEmpty()) {
              continue;
            }
            List<String> metricOrder = refs.stream().map(GraphSeriesRef::metricName).toList();
            metricOrder.forEach(assigned::add);
            List<String> rightAxis = refs.stream()
                .filter(GraphSeriesRef::rightAxis)
                .map(GraphSeriesRef::metricName)
                .toList();
            String title = normalizeDisplay(applyMacros(graph.name(), macros));
            if (title == null) {
              title = "Graph " + graphIndex + " [" + instance.instanceKey() + "]";
            }
            result.add(new MetricChartPanelDto(
                "prototype:" + safe(rule.key()) + ":" + safe(graph.uuid()) + ":" + safe(instance.instanceKey()),
                title,
                normalizeGraphType(graph.type()),
                metricOrder,
                rightAxis,
                List.of()
            ));
          }
        }
      }
    }

    if (template != null && template.graphs() != null) {
      int graphIndex = 0;
      for (ZabbixGraphRecord graph : template.graphs()) {
        graphIndex++;
        List<GraphSeriesRef> refs = materializeGraphItems(graph, Map.of(), knownMetrics, assigned);
        if (refs.isEmpty()) {
          continue;
        }
        List<String> metricOrder = refs.stream().map(GraphSeriesRef::metricName).toList();
        metricOrder.forEach(assigned::add);
        List<String> rightAxis = refs.stream()
            .filter(GraphSeriesRef::rightAxis)
            .map(GraphSeriesRef::metricName)
            .toList();
        String title = normalizeDisplay(graph.name());
        if (title == null) {
          title = "Graph " + graphIndex;
        }
        result.add(new MetricChartPanelDto(
            "static:" + safe(graph.uuid()),
            title,
            normalizeGraphType(graph.type()),
            metricOrder,
            rightAxis,
            List.of()
        ));
      }
    }

    for (String metricName : knownMetrics) {
      if (assigned.contains(metricName)) {
        continue;
      }
      String title = normalizeDisplay(metricDisplayNames == null ? null : metricDisplayNames.get(metricName));
      result.add(new MetricChartPanelDto(
          "single:" + safe(metricName),
          title == null ? metricName : title,
          "NORMAL",
          List.of(metricName),
          List.of(),
          List.of()
      ));
    }
    return List.copyOf(result);
  }

  private List<GraphSeriesRef> materializeGraphItems(
      ZabbixGraphRecord graph,
      Map<String, String> macros,
      Set<String> metricNames,
      Set<String> assigned
  ) {
    if (graph == null || graph.graphItems() == null || graph.graphItems().isEmpty()) {
      return List.of();
    }
    List<GraphSeriesRef> refs = new ArrayList<>();
    int fallbackSort = 0;
    for (ZabbixGraphItemRecord item : graph.graphItems()) {
      fallbackSort++;
      if (item == null || isGraphSum(item) || item.item() == null || item.item().key() == null || item.item().key().isBlank()) {
        continue;
      }
      String metricName = applyMacros(item.item().key(), macros);
      if (!metricNames.contains(metricName) || assigned.contains(metricName)) {
        continue;
      }
      refs.add(new GraphSeriesRef(metricName, parseSortOrder(item.sortorder(), fallbackSort), isRightAxis(item.yaxisside())));
    }
    return refs.stream()
        .sorted(Comparator.comparingInt(GraphSeriesRef::sortOrder))
        .toList();
  }

  private boolean isGraphSum(ZabbixGraphItemRecord item) {
    return item != null && "GRAPH_SUM".equalsIgnoreCase(trim(item.type()));
  }

  private boolean isRightAxis(String value) {
    return "RIGHT".equalsIgnoreCase(trim(value));
  }

  private String normalizeGraphType(String type) {
    String normalized = trim(type).toUpperCase(Locale.ROOT);
    return normalized.isEmpty() ? "NORMAL" : normalized;
  }

  private int parseSortOrder(String value, int fallback) {
    String normalized = trim(value);
    if (normalized.isEmpty()) {
      return fallback;
    }
    try {
      return Integer.parseInt(normalized);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private String applyMacros(String value, Map<String, String> macros) {
    if (value == null || value.isBlank() || macros == null || macros.isEmpty()) {
      return value;
    }
    String resolved = value;
    for (Map.Entry<String, String> macro : macros.entrySet()) {
      if (macro.getKey() == null || macro.getKey().isBlank() || macro.getValue() == null) {
        continue;
      }
      resolved = resolved.replace(macro.getKey(), macro.getValue());
    }
    return resolved;
  }

  private String normalizeDisplay(String value) {
    String normalized = trim(value).replaceAll("\\s+", " ");
    return normalized.isEmpty() || normalized.contains("{#") ? null : normalized;
  }

  private String trim(String value) {
    return value == null ? "" : value.trim();
  }

  private String safe(String value) {
    return value == null || value.isBlank() ? "na" : value;
  }

  /**
   * When {@code monitoring_discovery_instances} has no active rows (expired LLD, writer-only path, etc.),
   * we still recover macro values from concrete metric keys so graph_prototypes can be materialized.
   */
  private List<DiscoveryInstanceRuntime> inferSyntheticDiscoveryInstances(
      ZabbixDiscoveryRuleRuntime rule,
      Set<String> knownMetrics
  ) {
    if (rule == null || knownMetrics == null || knownMetrics.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> templates = new LinkedHashSet<>();
    if (rule.itemPrototypes() != null) {
      for (ZabbixItemRuntime proto : rule.itemPrototypes()) {
        addLldKeyTemplate(templates, proto == null ? null : proto.key());
      }
    }
    if (rule.graphPrototypes() != null) {
      for (ZabbixGraphRecord graph : rule.graphPrototypes()) {
        if (graph == null || graph.graphItems() == null) {
          continue;
        }
        for (ZabbixGraphItemRecord graphItem : graph.graphItems()) {
          if (graphItem == null || graphItem.item() == null) {
            continue;
          }
          addLldKeyTemplate(templates, graphItem.item().key());
        }
      }
    }
    if (templates.isEmpty()) {
      return List.of();
    }
    Map<String, Map<String, String>> macrosByInstance = new LinkedHashMap<>();
    for (String templateKey : templates) {
      for (String metricName : knownMetrics) {
        Optional<Map<String, String>> macros = matchMetricToKeyTemplate(metricName, templateKey);
        if (macros.isEmpty()) {
          continue;
        }
        String instanceKey = instanceKeyFromMacros(macros.get());
        macrosByInstance.merge(instanceKey, macros.get(), MetricChartLayoutBuilder::mergeMacroMaps);
      }
    }
    if (macrosByInstance.isEmpty()) {
      return List.of();
    }
    OffsetDateTime now = OffsetDateTime.now();
    List<DiscoveryInstanceRuntime> synthetic = new ArrayList<>();
    for (Map.Entry<String, Map<String, String>> entry : macrosByInstance.entrySet()) {
      synthetic.add(new DiscoveryInstanceRuntime(
          rule.key(),
          entry.getKey(),
          Map.copyOf(entry.getValue()),
          now,
          now.plusDays(1)
      ));
    }
    return List.copyOf(synthetic);
  }

  private static void addLldKeyTemplate(Set<String> sink, String keyTemplate) {
    if (keyTemplate == null || keyTemplate.isBlank() || !keyTemplate.contains("{#")) {
      return;
    }
    sink.add(keyTemplate);
  }

  private static Optional<Map<String, String>> matchMetricToKeyTemplate(String metricKey, String templateKey) {
    if (metricKey == null || templateKey == null) {
      return Optional.empty();
    }
    Matcher macroMatcher = Pattern.compile("\\{#[A-Z0-9_.]+\\}").matcher(templateKey);
    StringBuilder regexBuilder = new StringBuilder("^");
    List<String> macroNames = new ArrayList<>();
    int last = 0;
    while (macroMatcher.find()) {
      String literal = templateKey.substring(last, macroMatcher.start());
      regexBuilder.append(Pattern.quote(literal));
      regexBuilder.append("(.+?)");
      macroNames.add(macroMatcher.group());
      last = macroMatcher.end();
    }
    regexBuilder.append(Pattern.quote(templateKey.substring(last)));
    regexBuilder.append("$");
    Matcher concrete = Pattern.compile(regexBuilder.toString()).matcher(metricKey);
    if (!concrete.matches()) {
      return Optional.empty();
    }
    Map<String, String> macros = new LinkedHashMap<>();
    for (int i = 0; i < macroNames.size(); i++) {
      macros.put(macroNames.get(i), concrete.group(i + 1));
    }
    return Optional.of(macros);
  }

  private static Map<String, String> mergeMacroMaps(Map<String, String> left, Map<String, String> right) {
    Map<String, String> merged = new LinkedHashMap<>(left);
    if (right == null || right.isEmpty()) {
      return merged;
    }
    for (Map.Entry<String, String> entry : right.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
        continue;
      }
      String existing = merged.get(entry.getKey());
      if (existing != null && !existing.equals(entry.getValue())) {
        continue;
      }
      merged.put(entry.getKey(), entry.getValue());
    }
    return merged;
  }

  private static String instanceKeyFromMacros(Map<String, String> macros) {
    if (macros == null || macros.isEmpty()) {
      return "na";
    }
    String snmp = firstNonBlank(macros.get("{#SNMPINDEX}"));
    if (snmp != null) {
      return snmp;
    }
    String ifName = firstNonBlank(macros.get("{#IFNAME}"));
    if (ifName != null) {
      return ifName;
    }
    return macros.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((a, b) -> a + "&" + b)
        .orElse("na");
  }

  private static String firstNonBlank(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return value;
  }

  private record GraphSeriesRef(String metricName, int sortOrder, boolean rightAxis) {
  }
}
