package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryConditionRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryFilterRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixLldMacroPathRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixPreprocessingStep;
import com.networkscanner.backend.network.scan.util.SnmpWalkJsonSupport;
import com.networkscanner.backend.monitoring.util.ZabbixTemplateMacroSupport;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs DEPENDENT LLD from fixture walk JSON (avoids live SNMP; internal {@code readOidValues} is not spied).
 */
final class LinuxBySnmpDependentDiscoveryRunner {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private LinuxBySnmpDependentDiscoveryRunner() {
  }

  static List<DiscoveryInstanceRuntime> execute(
      ResolvedMonitoringTemplate template,
      ZabbixDiscoveryRuleRuntime discoveryRule,
      Map<String, String> getByKey,
      Map<String, String> walkByKey,
      OffsetDateTime timestamp
  ) {
    if (discoveryRule.masterItemKey() == null) {
      return List.of();
    }
    String masterPayload = resolveMasterPayload(template, discoveryRule.masterItemKey(), getByKey, walkByKey);
    if (masterPayload == null || masterPayload.isBlank()) {
      return List.of();
    }
    ZabbixItemRuntime masterItem = template.items().get(discoveryRule.masterItemKey());
    if (masterItem == null) {
      return List.of();
    }
    try {
      String processed = applyDiscoveryPreprocessing(masterPayload, discoveryRule, masterItem, template);
      JsonNode root = MAPPER.readTree(processed);
      if (!root.isArray()) {
        return List.of();
      }
      Map<String, String> templateMacros =
          template.templateMacros() == null ? Map.of() : template.templateMacros();
      List<DiscoveryInstanceRuntime> instances = new ArrayList<>();
      int ordinal = 0;
      for (JsonNode row : root) {
        if (!row.isObject()) {
          continue;
        }
        Map<String, String> macros = mapLldMacros(row, discoveryRule.lldMacroPaths());
        if (macros.isEmpty()) {
          continue;
        }
        if (!matchesDiscoveryFilter(discoveryRule.filter(), macros, templateMacros)) {
          continue;
        }
        String instanceKey = firstNonBlank(macros.get("{#SNMPINDEX}"), String.valueOf(++ordinal));
        instances.add(new DiscoveryInstanceRuntime(
            discoveryRule.key(),
            instanceKey,
            Map.copyOf(macros),
            timestamp,
            timestamp.plusSeconds(Math.max(discoveryRule.lifetimeSeconds(), 1))
        ));
      }
      return List.copyOf(instances);
    } catch (Exception exception) {
      return List.of();
    }
  }

  private static boolean matchesDiscoveryFilter(
      ZabbixDiscoveryFilterRecord filter,
      Map<String, String> instanceMacros,
      Map<String, String> templateMacros
  ) {
    if (filter == null || filter.conditions() == null || filter.conditions().isEmpty()) {
      return true;
    }
    boolean orMode = "OR".equalsIgnoreCase(filter.evaltype());
    for (ZabbixDiscoveryConditionRecord condition : filter.conditions()) {
      if (condition == null) {
        continue;
      }
      String expected = condition.value() == null ? "" : condition.value();
      expected = ZabbixTemplateMacroSupport.applyTemplateMacros(expected, templateMacros);
      for (Map.Entry<String, String> entry : instanceMacros.entrySet()) {
        expected = expected.replace(entry.getKey(), entry.getValue());
      }
      boolean matches = matchesDiscoveryCondition(condition.macro(), expected, condition.operator(), instanceMacros);
      if (orMode && matches) {
        return true;
      }
      if (!orMode && !matches) {
        return false;
      }
    }
    return !orMode;
  }

  private static boolean matchesDiscoveryCondition(
      String macro,
      String expected,
      String operator,
      Map<String, String> instanceMacros
  ) {
    if (macro == null || macro.isBlank()) {
      return true;
    }
    String actual = instanceMacros.getOrDefault(macro, "");
    String op = operator == null ? "MATCHES_REGEX" : operator.trim().toUpperCase();
    return switch (op) {
      case "NOT_MATCHES_REGEX", "NOT_MATCHES" -> !safeRegexMatches(actual, expected);
      case "EQUALS" -> actual.equals(expected);
      case "NOT_EQUALS" -> !actual.equals(expected);
      case "EXISTS" -> instanceMacros.containsKey(macro) && !actual.isBlank();
      case "NOT_EXISTS" -> !instanceMacros.containsKey(macro) || actual.isBlank();
      default -> safeRegexMatches(actual, expected);
    };
  }

  private static boolean safeRegexMatches(String actual, String expected) {
    if (expected == null || expected.isBlank()) {
      return true;
    }
    try {
      return actual.matches(expected);
    } catch (java.util.regex.PatternSyntaxException exception) {
      return true;
    }
  }

  static String resolveMasterPayload(
      ResolvedMonitoringTemplate template,
      String itemKey,
      Map<String, String> getByKey,
      Map<String, String> walkByKey
  ) {
    ZabbixItemRuntime item = template.items().get(itemKey);
    if (item == null) {
      return null;
    }
    if (item.snmpOid() != null && !item.snmpOid().isBlank()) {
      String raw = walkByKey.containsKey(itemKey) ? walkByKey.get(itemKey) : getByKey.get(itemKey);
      if (raw == null) {
        return null;
      }
      return applyItemPreprocessing(template, item, raw);
    }
    if (!item.isDependent() || item.masterItemKey() == null) {
      return null;
    }
    String parentRaw = resolveMasterPayload(template, item.masterItemKey(), getByKey, walkByKey);
    if (parentRaw == null || parentRaw.isBlank()) {
      return null;
    }
    MonitoringPreprocessingEngine engine = new MonitoringPreprocessingEngine();
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        template.id(),
        item,
        item.key(),
        item.key(),
        "",
        null,
        template.items().get(item.masterItemKey()) == null ? null : template.items().get(item.masterItemKey()).snmpOid(),
        Map.of()
    );
    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        item,
        parentRaw,
        null,
        OffsetDateTime.now(),
        new MonitoringPreprocessContext(template, materialized)
    );
    if (processed.discarded()) {
      return null;
    }
    if (processed.textValue() != null && !processed.textValue().isBlank()) {
      return processed.textValue();
    }
    if (processed.numericValue() != null) {
      return String.valueOf(processed.numericValue());
    }
    return null;
  }

  private static String applyItemPreprocessing(
      ResolvedMonitoringTemplate template,
      ZabbixItemRuntime item,
      String raw
  ) {
    if (item.preprocessing() == null || item.preprocessing().isEmpty()) {
      return raw;
    }
    MonitoringPreprocessingEngine engine = new MonitoringPreprocessingEngine();
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        template.id(),
        item,
        item.key(),
        item.key(),
        "",
        null,
        item.snmpOid(),
        Map.of()
    );
    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed = engine.process(
        item,
        raw,
        null,
        OffsetDateTime.now(),
        new MonitoringPreprocessContext(template, materialized)
    );
    if (processed.discarded()) {
      return null;
    }
    if (processed.textValue() != null && !processed.textValue().isBlank()) {
      return processed.textValue();
    }
    return raw;
  }

  private static String applyDiscoveryPreprocessing(
      String masterPayload,
      ZabbixDiscoveryRuleRuntime discoveryRule,
      ZabbixItemRuntime masterItem,
      ResolvedMonitoringTemplate template
  ) throws Exception {
    String current = masterPayload;
    MonitoringPreprocessingEngine engine = new MonitoringPreprocessingEngine();
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        template.id(),
        masterItem,
        masterItem.key(),
        masterItem.key(),
        "",
        null,
        masterItem.snmpOid(),
        Map.of()
    );
    MonitoringPreprocessContext ctx = new MonitoringPreprocessContext(template, materialized);
    for (ZabbixPreprocessingStep step : discoveryRule.preprocessing()) {
      if (step == null || step.type() == null) {
        continue;
      }
      String type = step.type().trim().toUpperCase();
      if ("SNMP_WALK_TO_JSON".equals(type)) {
        current = applySnmpWalkToJson(current, step, masterItem);
      } else if ("JAVASCRIPT".equals(type)) {
        String script = step.parameters() == null || step.parameters().isEmpty() ? "" : step.parameters().get(0);
        JsPreprocessingCompatService.JsResult js =
            new JsPreprocessingCompatService(java.util.Optional.empty()).execute(current, script, Map.of());
        if ("ok".equals(js.status()) && js.value() != null) {
          current = js.value();
        }
      }
    }
    return current;
  }

  private static String applySnmpWalkToJson(
      String walkJson,
      ZabbixPreprocessingStep step,
      ZabbixItemRuntime masterItem
  ) throws Exception {
    List<String> columnOids = parseWalkColumns(masterItem.snmpOid());
    List<String[]> triplets = SnmpWalkJsonSupport.parseSnmpWalkToJsonTriplets(step.parameters());
    if (columnOids.isEmpty() || triplets.isEmpty()) {
      return walkJson;
    }
    JsonNode root = MAPPER.readTree(walkJson);
    if (!root.isArray()) {
      return walkJson;
    }
    ArrayNode out = MAPPER.createArrayNode();
    for (JsonNode row : root) {
      if (!row.isObject()) {
        continue;
      }
      ObjectNode obj = MAPPER.createObjectNode();
      JsonNode indexNode = row.get("index");
      if (indexNode != null && !indexNode.isNull()) {
        obj.put("{#SNMPINDEX}", indexNode.asText());
      }
      for (String[] triplet : triplets) {
        String macroKey = triplet[0];
        String oid = triplet[1];
        String defaultValue = triplet[2];
        String field = fieldForColumn(columnOids, oid);
        String cell = field == null ? null : SnmpWalkJsonSupport.jsonCellToString(row.get(field));
        if (cell == null) {
          cell = defaultValue;
        }
        if (macroKey != null && !macroKey.isBlank()) {
          obj.put(macroKey, cell == null ? "" : cell);
        }
      }
      out.add(obj);
    }
    return MAPPER.writeValueAsString(out);
  }

  private static List<String> parseWalkColumns(String snmpOid) {
    if (snmpOid == null) {
      return List.of();
    }
    String trimmed = snmpOid.trim();
    if (!trimmed.startsWith("walk[") || !trimmed.endsWith("]")) {
      return List.of();
    }
    String body = trimmed.substring("walk[".length(), trimmed.length() - 1);
    return java.util.Arrays.stream(body.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .toList();
  }

  private static String fieldForColumn(List<String> columnOids, String targetOid) {
    for (int i = 0; i < columnOids.size(); i++) {
      if (targetOid.equals(columnOids.get(i))) {
        return com.networkscanner.backend.monitoring.util.LinuxBySnmpWalkSpecs.fieldNameForColumn(
            targetOid,
            i
        );
      }
    }
    return null;
  }

  private static Map<String, String> mapLldMacros(
      JsonNode row,
      List<ZabbixLldMacroPathRecord> macroPaths
  ) {
    if (macroPaths == null || macroPaths.isEmpty()) {
      Map<String, String> macros = new LinkedHashMap<>();
      row.fields().forEachRemaining(entry -> {
        String key = entry.getKey();
        if (key != null && key.startsWith("{#")) {
          String v = SnmpWalkJsonSupport.jsonCellToString(entry.getValue());
          if (v != null) {
            macros.put(key, v);
          }
        }
      });
      if (!macros.containsKey("{#SNMPINDEX}")) {
        JsonNode index = row.get("index");
        if (index != null) {
          macros.put("{#SNMPINDEX}", index.asText());
        }
      }
      return macros;
    }
    Map<String, String> macros = new LinkedHashMap<>();
    for (ZabbixLldMacroPathRecord path : macroPaths) {
      if (path == null || path.lldMacro() == null || path.path() == null) {
        continue;
      }
      String value = readJsonPath(row, path.path());
      if (value != null) {
        macros.put(path.lldMacro(), value);
      }
    }
    return macros;
  }

  private static String readJsonPath(JsonNode row, String path) {
    String trimmed = path == null ? "" : path.trim();
    if (trimmed.startsWith("$.")) {
      trimmed = trimmed.substring(2);
    }
    JsonNode node = row.get(trimmed);
    return node == null ? null : SnmpWalkJsonSupport.jsonCellToString(node);
  }

  private static String firstNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    return fallback;
  }
}
