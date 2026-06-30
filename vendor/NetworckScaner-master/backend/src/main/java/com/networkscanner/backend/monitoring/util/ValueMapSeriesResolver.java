package com.networkscanner.backend.monitoring.util;

import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ValueMapSeriesMeta;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixValueMapRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves valuemap metadata for a materialized metric key (static item or LLD prototype instance).
 */
public final class ValueMapSeriesResolver {

  private ValueMapSeriesResolver() {
  }

  public static ValueMapSeriesMeta resolve(ResolvedMonitoringTemplate template, String metricName) {
    if (template == null || metricName == null || metricName.isBlank()) {
      return null;
    }
    String valueMapName = resolveValueMapName(template, metricName);
    if (valueMapName == null || valueMapName.isBlank()) {
      return null;
    }
    Map<String, String> mappings = resolveMappings(template, valueMapName);
    if (mappings == null || mappings.isEmpty()) {
      return null;
    }
    return new ValueMapSeriesMeta(valueMapName, Map.copyOf(mappings));
  }

  public static Map<String, ValueMapSeriesMeta> resolveAll(
      ResolvedMonitoringTemplate template,
      Iterable<String> metricNames
  ) {
    if (template == null || metricNames == null) {
      return Map.of();
    }
    Map<String, ValueMapSeriesMeta> out = new LinkedHashMap<>();
    for (String metricName : metricNames) {
      if (metricName == null || metricName.isBlank()) {
        continue;
      }
      ValueMapSeriesMeta meta = resolve(template, metricName);
      if (meta != null) {
        out.put(metricName, meta);
      }
    }
    return Map.copyOf(out);
  }

  private static String resolveValueMapName(ResolvedMonitoringTemplate template, String metricName) {
    if (template.items() != null) {
      ZabbixItemRuntime item = template.items().get(metricName);
      if (item != null && item.valueMapName() != null && !item.valueMapName().isBlank()) {
        return item.valueMapName();
      }
    }
    if (template.discoveryRules() == null || template.discoveryRules().isEmpty()) {
      return null;
    }
    for (ZabbixDiscoveryRuleRuntime rule : template.discoveryRules().values()) {
      if (rule == null || rule.itemPrototypes() == null) {
        continue;
      }
      for (ZabbixItemRuntime prototype : rule.itemPrototypes()) {
        if (prototype == null || prototype.key() == null || prototype.key().isBlank()) {
          continue;
        }
        if (prototype.valueMapName() == null || prototype.valueMapName().isBlank()) {
          continue;
        }
        if (matchMetricToKeyTemplate(metricName, prototype.key()).isPresent()) {
          return prototype.valueMapName();
        }
      }
    }
    return null;
  }

  private static Map<String, String> resolveMappings(ResolvedMonitoringTemplate template, String valueMapName) {
    if (template.valueMaps() == null || valueMapName == null || valueMapName.isBlank()) {
      return null;
    }
    ZabbixValueMapRuntime valueMap = template.valueMaps().get(valueMapName);
    if (valueMap == null || valueMap.mappings() == null || valueMap.mappings().isEmpty()) {
      return null;
    }
    return valueMap.mappings();
  }

  static Optional<Map<String, String>> matchMetricToKeyTemplate(String metricKey, String templateKey) {
    if (metricKey == null || templateKey == null || !templateKey.contains("{#")) {
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
}
