package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixPreprocessingStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import java.io.StringReader;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class MonitoringPreprocessingEngine {

  private static final Logger log = LoggerFactory.getLogger(MonitoringPreprocessingEngine.class);
  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Pattern JSONPATH_PATTERN = Pattern.compile("^\\$\\.([\\w.-]+)$");
  private static final Pattern JSONPATH_FILTER_FIRST_PATTERN = Pattern.compile(
      "^\\$\\[\\?\\(@\\.([\\w.-]+)\\s*==\\s*['\\\"]([^'\\\"]+)['\\\"]\\)\\]\\.([\\w.-]+)\\.first\\(\\)$"
  );
  private static final Set<String> TRUE_BOOLEAN_VALUES = Set.of(
      "true", "t", "yes", "y", "on", "up", "running", "enabled", "available", "ok", "master"
  );
  private static final Set<String> FALSE_BOOLEAN_VALUES = Set.of(
      "false", "f", "no", "n", "off", "down", "unused", "disabled", "unavailable", "err", "slave"
  );
  private static final Set<String> UNSUPPORTED_VALUE_TOKENS = new HashSet<>(Set.of(
      "nosuchobject", "nosuchinstance", "endofmibview", "null", "timeout", "timed out", "connection refused"
  ));
  private static final Map<String, String> WALK_OID_FIELD_ALIASES = Map.of(
      "1.3.6.1.4.1.2021.10.1.2", "laName",
      "1.3.6.1.4.1.2021.10.1.3", "laLoad",
      "1.3.6.1.4.1.2021.9.1.1", "index",
      "1.3.6.1.4.1.2021.9.1.2", "dskPath",
      "1.3.6.1.4.1.2021.9.1.3", "dskDevice"
  );
  /** IF-MIB::ifHighSpeed (Mbps); на Linux VM часто 0 — тогда берём ifSpeed (bps) из walk. */
  private static final String OID_IF_HIGH_SPEED = "1.3.6.1.2.1.31.1.1.15";
  private static final String OID_IF_SPEED = "1.3.6.1.2.1.2.2.1.5";
  /** ifSpeed уже в bps; не умножать на 1_000_000 как ifHighSpeed (Mbps). */
  private static final double IF_SPEED_ALREADY_BPS_THRESHOLD = 1_000_000d;
  private final JsPreprocessingCompatService jsPreprocessingCompatService;

  public MonitoringPreprocessingEngine() {
    this(new JsPreprocessingCompatService(Optional.empty()));
  }

  public MonitoringPreprocessingEngine(JsPreprocessingCompatService jsPreprocessingCompatService) {
    this.jsPreprocessingCompatService = jsPreprocessingCompatService;
  }

  public ProcessedMonitoringValue process(
      ZabbixItemRuntime runtime,
      String rawValue,
      ItemStateSnapshot previous,
      OffsetDateTime now
  ) {
    return process(runtime, rawValue, previous, now, MonitoringPreprocessContext.NONE);
  }

  public ProcessedMonitoringValue process(
      ZabbixItemRuntime runtime,
      String rawValue,
      ItemStateSnapshot previous,
      OffsetDateTime now,
      MonitoringPreprocessContext context
  ) {
    String textValue = rawValue == null ? "" : rawValue.trim();
    String status = "ok";
    String note = null;
    MonitoringPreprocessContext ctx = context == null ? MonitoringPreprocessContext.NONE : context;

    String itemStateTextOverride = null;
    if (runtime.preprocessing() != null) {
      for (var step : runtime.preprocessing()) {
        StepResult stepResult = applyStep(step, textValue, previous, now, ctx);
        textValue = stepResult.value();
        if (stepResult.itemStateTextOverride() != null) {
          itemStateTextOverride = stepResult.itemStateTextOverride();
        }
        if (!"ok".equals(stepResult.status()) && !"discarded".equals(stepResult.status())) {
          status = stepResult.status();
          note = mergeNotes(note, stepResult.note());
        }
        if ("discarded".equals(stepResult.status())) {
          status = "discarded";
          note = mergeNotes(note, stepResult.note());
          break;
        }
      }
    }

    String textForItemState = itemStateTextOverride != null ? itemStateTextOverride : textValue;
    if ("discarded".equals(status)) {
      return new ProcessedMonitoringValue(null, textForItemState, true, status, note);
    }
    if (runtime.isTextual()) {
      return new ProcessedMonitoringValue(null, textValue, false, status, note);
    }
    return new ProcessedMonitoringValue(parseDouble(textValue, 0.0d), textForItemState, false, status, note);
  }

  private StepResult applyStep(
      ZabbixPreprocessingStep step,
      String current,
      ItemStateSnapshot previous,
      OffsetDateTime now,
      MonitoringPreprocessContext ctx
  ) {
    String value = current == null ? "" : current;
    String type = normalize(step.type());
    String parameter = param(step, 0);
    try {
      return switch (type) {
        case "MULTIPLIER" -> {
          double numeric = parseDouble(value, 0.0d);
          double factor = parseDouble(parameter, 1.0d);
          if (factor == 1_000_000d && numeric >= IF_SPEED_ALREADY_BPS_THRESHOLD) {
            yield StepResult.ok(value);
          }
          yield StepResult.ok(String.valueOf(numeric * factor));
        }
        case "SIMPLE_CHANGE" -> {
          if (previous != null && previous.textValue() != null) {
            double delta = parseDouble(value, 0.0d) - parseDouble(previous.textValue(), 0.0d);
            if (delta < 0) {
              /* Счётчик уменьшился (сброс агента/перезагрузка): иначе discard не пишет state — вечный отрицательный delta. */
              yield StepResult.okWithItemStateText("0", value);
            }
            yield StepResult.okWithItemStateText(String.valueOf(delta), value);
          }
          yield StepResult.okWithItemStateText("0", value);
        }
        case "CHANGE_PER_SECOND" -> {
          if (previous != null && previous.textValue() != null && previous.lastCollectedAt() != null) {
            long seconds = Math.max(1L, ChronoUnit.SECONDS.between(previous.lastCollectedAt(), now));
            double delta = parseDouble(value, 0.0d) - parseDouble(previous.textValue(), 0.0d);
            if (delta < 0) {
              yield StepResult.okWithItemStateText("0", value);
            }
            yield StepResult.okWithItemStateText(String.valueOf(delta / seconds), value);
          }
          yield StepResult.okWithItemStateText("0", value);
        }
        case "TRIM" -> StepResult.ok(value.trim());
        case "LTRIM" -> StepResult.ok(trimLeft(value, parameter));
        case "RTRIM" -> StepResult.ok(trimRight(value, parameter));
        case "STR_REPLACE" -> StepResult.ok(value.replace(parameter, param(step, 1)));
        case "REGEX" -> applyRegex(value, parameter, param(step, 1, "$1"));
        case "MATCHES_REGEX" -> applyMatchesRegex(value, parameter, true);
        case "NOT_MATCHES_REGEX" -> applyMatchesRegex(value, parameter, false);
        case "IN_RANGE" -> applyInRange(value, parameter);
        case "JSONPATH" -> applyJsonPath(value, parameter);
        case "XMLPATH" -> applyXmlPath(value, parameter);
        case "CHECK_JSON_ERROR" -> checkJsonError(value, parameter);
        case "CHECK_XML_ERROR" -> checkXmlError(value, parameter);
        case "CHECK_REGEX_ERROR" -> checkRegexError(value, parameter, param(step, 1, "$1"));
        case "CHECK_NOT_SUPPORTED" -> checkNotSupported(step, value);
        case "JAVASCRIPT" -> applyJavaScript(value, step, ctx);
        case "XML_TO_JSON" -> xmlToJson(value);
        case "CSV_TO_JSON" -> csvToJson(value);
        case "BOOL_TO_DECIMAL" -> applyBooleanToDecimal(value);
        case "HEX_TO_DECIMAL" -> StepResult.ok(String.valueOf(parseHex(value)));
        case "OCTAL_TO_DECIMAL" -> StepResult.ok(String.valueOf(parseOctal(value)));
        case "DISCARD_UNCHANGED" -> {
          if (previous != null && previous.textValue() != null && previous.textValue().equals(value)) {
            yield new StepResult(value, "discarded", "DISCARD_UNCHANGED: unchanged value", null);
          }
          yield StepResult.ok(value);
        }
        case "DISCARD_UNCHANGED_HEARTBEAT" -> applyDiscardUnchangedWithHeartbeat(value, previous, now, parameter, ctx);
        case "SNMP_WALK_TO_JSON" -> applySnmpWalkToJson(step, value, ctx);
        case "SNMP_WALK_VALUE" -> applySnmpWalkValue(step, value, ctx);
        default -> {
          String note = "Unsupported preprocessing step: " + type;
          log.debug(note);
          yield StepResult.fallback(value, note);
        }
      };
    } catch (Exception exception) {
      return applyErrorHandler(step, value, type, exception.getMessage());
    }
  }

  /**
   * Извлекает значение из JSON мастер-item {@code walk[...]} по OID колонки и {#SNMPINDEX} (как в Zabbix).
   */
  private StepResult applySnmpWalkValue(
      ZabbixPreprocessingStep step,
      String masterJson,
      MonitoringPreprocessContext ctx
  ) {
    if (ctx.template() == null || ctx.materializedItem() == null) {
      return StepResult.fallback(masterJson, "SNMP_WALK_VALUE requires template and materialized item");
    }
    String masterKey = ctx.materializedItem().runtime().masterItemKey();
    if (masterKey == null || masterKey.isBlank()) {
      return StepResult.fallback(masterJson, "SNMP_WALK_VALUE: missing master_item");
    }
    ZabbixItemRuntime masterRt = ctx.template().item(masterKey);
    if (masterRt == null || masterRt.snmpOid() == null) {
      return StepResult.fallback(masterJson, "SNMP_WALK_VALUE: master item not found");
    }
    List<String> columns = parseWalkColumnOids(masterRt.snmpOid());
    if (columns.isEmpty()) {
      return StepResult.fallback(masterJson, "SNMP_WALK_VALUE: master snmp_oid is not walk[...]");
    }
    String oidWithMacros = applyItemMacros(param(step, 0), ctx.materializedItem().macros());
    int colIndex = findWalkValueColumnIndex(columns, oidWithMacros);
    if (colIndex < 0) {
      return StepResult.ok(param(step, 1, "0"));
    }
    String matchedColumnOid = columns.get(colIndex);
    String colField = fieldNameForWalkColumnOid(columns, matchedColumnOid);
    if (colField == null) {
      return StepResult.ok(param(step, 1, "0"));
    }
    String requestedRowIndex = resolveRequestedWalkRowIndex(oidWithMacros, matchedColumnOid);
    String defaultValue = param(step, 1, "0");
    String itemKey = ctx.materializedItem().key();
    boolean discardEmptyMasterDefault = itemKey != null
        && (itemKey.contains("net.if.type") || itemKey.contains("net.if.duplex"));
    try {
      JsonNode root = OBJECT_MAPPER.readTree(masterJson);
      if (!root.isArray()) {
        return snmpWalkValueFallback(defaultValue, discardEmptyMasterDefault, 0);
      }
      int rowCount = root.size();
      if (requestedRowIndex == null) {
        for (JsonNode row : root) {
          if (!row.isObject()) {
            continue;
          }
          JsonNode cell = row.get(colField);
          if (cell != null && !cell.isNull()) {
            return StepResult.ok(resolveIfSpeedWalkCell(columns, oidWithMacros, row, cell));
          }
        }
        return snmpWalkValueFallback(defaultValue, discardEmptyMasterDefault, rowCount);
      }
      for (JsonNode row : root) {
        if (!row.isObject()) {
          continue;
        }
        JsonNode idxNode = row.get("index");
        if (idxNode == null || !requestedRowIndex.equals(idxNode.asText())) {
          continue;
        }
        JsonNode cell = row.get(colField);
        if (cell == null || cell.isNull()) {
          String fallback = resolveIfSpeedWalkCell(columns, oidWithMacros, row, null);
          if (!isBlankOrZeroSnmpValue(fallback)) {
            return StepResult.ok(fallback);
          }
          return snmpWalkValueFallback(defaultValue, discardEmptyMasterDefault, rowCount);
        }
        return StepResult.ok(resolveIfSpeedWalkCell(columns, oidWithMacros, row, cell));
      }
      return snmpWalkValueFallback(defaultValue, discardEmptyMasterDefault, rowCount);
    } catch (Exception exception) {
      log.debug("SNMP_WALK_VALUE: failed to parse master JSON, using default: {}", exception.getMessage());
      return snmpWalkValueFallback(defaultValue, discardEmptyMasterDefault, 0);
    }
  }

  /**
   * Не пишем «0» (unknown) в историю при пустом master walk — иначе графики Interface type/duplex скачут.
   */
  private static StepResult snmpWalkValueFallback(String defaultValue, boolean discardWhenEmptyMaster, int masterRowCount) {
    if (discardWhenEmptyMaster && masterRowCount == 0) {
      return new StepResult(defaultValue, "discarded", "SNMP_WALK_VALUE: empty master walk", null);
    }
    return StepResult.ok(defaultValue);
  }

  /**
   * Column in {@code walk[...]} that supplies the cell: exact OID, OID + instance suffix, or legacy base match.
   */
  private static int findWalkValueColumnIndex(List<String> columns, String oidWithMacros) {
    if (columns == null || oidWithMacros == null || oidWithMacros.isBlank()) {
      return -1;
    }
    for (int i = 0; i < columns.size(); i++) {
      if (oidWithMacros.equals(columns.get(i))) {
        return i;
      }
    }
    int best = -1;
    int bestLen = -1;
    for (int i = 0; i < columns.size(); i++) {
      String col = columns.get(i);
      if (oidWithMacros.startsWith(col + ".") && col.length() > bestLen) {
        best = i;
        bestLen = col.length();
      }
    }
    if (best >= 0) {
      return best;
    }
    ParsedWalkOid parsed = splitTrailingNumericIndex(oidWithMacros);
    if (parsed == null) {
      return -1;
    }
    for (int i = 0; i < columns.size(); i++) {
      if (parsed.baseOid().equals(columns.get(i))) {
        return i;
      }
    }
    return -1;
  }

  /**
   * Row {@code index} in walk JSON to match, or {@code null} when the target is a scalar column (same value on every row).
   */
  private static String resolveRequestedWalkRowIndex(String oidWithMacros, String matchedColumnOid) {
    if (oidWithMacros.equals(matchedColumnOid) && matchedColumnOid.endsWith(".0")) {
      return null;
    }
    if (oidWithMacros.startsWith(matchedColumnOid + ".")) {
      return oidWithMacros.substring(matchedColumnOid.length() + 1);
    }
    ParsedWalkOid parsed = splitTrailingNumericIndex(oidWithMacros);
    return parsed == null ? null : parsed.index();
  }

  private StepResult applySnmpWalkToJson(
      ZabbixPreprocessingStep step,
      String masterJson,
      MonitoringPreprocessContext ctx
  ) {
    if (ctx.template() == null || ctx.materializedItem() == null) {
      return StepResult.fallback(masterJson, "SNMP_WALK_TO_JSON requires template and materialized item");
    }
    String masterKey = ctx.materializedItem().runtime().masterItemKey();
    ZabbixItemRuntime masterRt;
    if (masterKey == null || masterKey.isBlank()) {
      masterRt = ctx.materializedItem().runtime();
    } else {
      masterRt = ctx.template().item(masterKey);
      if (masterRt == null) {
        return StepResult.fallback(masterJson, "SNMP_WALK_TO_JSON: master item not found");
      }
    }
    if (masterRt.snmpOid() == null || masterRt.snmpOid().isBlank()) {
      return StepResult.fallback(masterJson, "SNMP_WALK_TO_JSON: walk snmp_oid missing");
    }
    List<String> columns = parseWalkColumnOids(masterRt.snmpOid());
    if (columns.isEmpty()) {
      return StepResult.fallback(masterJson, "SNMP_WALK_TO_JSON: master snmp_oid is not walk[...]");
    }
    List<String[]> triplets = parseSnmpWalkToJsonTriplets(step.parameters());
    if (triplets.isEmpty()) {
      return StepResult.ok(masterJson);
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(masterJson);
      if (!root.isArray()) {
        return StepResult.ok(masterJson);
      }
      var out = OBJECT_MAPPER.createArrayNode();
      for (JsonNode row : root) {
        if (!row.isObject()) {
          continue;
        }
        var obj = OBJECT_MAPPER.createObjectNode();
        JsonNode indexNode = row.get("index");
        if (indexNode != null && !indexNode.isNull()) {
          obj.put("{#SNMPINDEX}", indexNode.isValueNode() ? indexNode.asText() : indexNode.toString());
        }
        for (String[] triplet : triplets) {
          String macroKey = triplet[0];
          String oid = triplet[1];
          String defaultValue = triplet[2];
          String field = fieldNameForWalkColumnOid(columns, oid);
          String cell = field == null ? null : jsonCellToString(row.get(field));
          if (cell == null) {
            cell = defaultValue;
          }
          if (macroKey != null && !macroKey.isBlank()) {
            obj.put(macroKey, cell == null ? "" : cell);
          }
        }
        out.add(obj);
      }
      return StepResult.ok(OBJECT_MAPPER.writeValueAsString(out));
    } catch (Exception exception) {
      log.debug("SNMP_WALK_TO_JSON: failed to materialize walk JSON, returning master: {}", exception.getMessage());
      return StepResult.ok(masterJson);
    }
  }

  private static String applyItemMacros(String expression, Map<String, String> macros) {
    if (expression == null) {
      return "";
    }
    String result = expression;
    if (macros != null) {
      for (Map.Entry<String, String> entry : macros.entrySet()) {
        if (entry.getKey() != null && entry.getValue() != null) {
          result = result.replace(entry.getKey(), entry.getValue());
        }
      }
    }
    return result;
  }

  private static List<String> parseWalkColumnOids(String snmpOid) {
    if (snmpOid == null) {
      return List.of();
    }
    String trimmed = snmpOid.trim();
    if (!trimmed.startsWith("walk[") || !trimmed.endsWith("]")) {
      return List.of();
    }
    String body = trimmed.substring("walk[".length(), trimmed.length() - 1).trim();
    if (body.isBlank()) {
      return List.of();
    }
    return java.util.Arrays.stream(body.split(","))
        .map(String::trim)
        .filter(token -> !token.isBlank())
        .toList();
  }

  private static ParsedWalkOid splitTrailingNumericIndex(String dottedOid) {
    if (dottedOid == null || dottedOid.isBlank()) {
      return null;
    }
    int lastDot = dottedOid.lastIndexOf('.');
    if (lastDot <= 0) {
      return null;
    }
    String base = dottedOid.substring(0, lastDot);
    String idx = dottedOid.substring(lastDot + 1);
    if (!idx.chars().allMatch(Character::isDigit)) {
      return null;
    }
    return new ParsedWalkOid(base, idx);
  }

  private record ParsedWalkOid(String baseOid, String index) {
  }

  private static List<String[]> parseSnmpWalkToJsonTriplets(List<String> parameters) {
    if (parameters == null || parameters.isEmpty()) {
      return List.of();
    }
    List<String[]> list = new ArrayList<>();
    for (int i = 0; i + 2 < parameters.size(); i += 3) {
      String macro = parameters.get(i) == null ? "" : parameters.get(i).trim();
      String oid = parameters.get(i + 1) == null ? "" : parameters.get(i + 1).trim();
      String def = parameters.get(i + 2) == null ? "0" : parameters.get(i + 2).trim();
      if (macro.isBlank() || oid.isBlank()) {
        continue;
      }
      list.add(new String[] {macro, oid, def.isEmpty() ? "0" : def});
    }
    return list;
  }

  private String fieldNameForWalkColumnOid(List<String> columnOids, String targetOid) {
    if (targetOid == null || targetOid.isBlank()) {
      return null;
    }
    String normalized = targetOid.trim();
    for (int i = 0; i < columnOids.size(); i++) {
      if (normalized.equals(columnOids.get(i))) {
        String alias = WALK_OID_FIELD_ALIASES.get(columnOids.get(i));
        return alias == null ? "col" + (i + 1) : alias;
      }
    }
    return null;
  }

  private static String jsonCellToString(JsonNode node) {
    if (node == null || node.isNull() || node.isMissingNode()) {
      return null;
    }
    return node.isValueNode() ? node.asText() : node.toString();
  }

  private StepResult applyRegex(String value, String regex, String replacement) {
    Matcher matcher = Pattern.compile(regex).matcher(value);
    if (matcher.find()) {
      return StepResult.ok(matcher.replaceFirst(replacement));
    }
    return new StepResult(value, "error", "REGEX pattern did not match", null);
  }

  private StepResult applyJavaScript(String value, ZabbixPreprocessingStep step, MonitoringPreprocessContext ctx) {
    String scriptBody = param(step, 0);
    Map<String, String> macros = ctx.materializedItem() == null ? Map.of() : ctx.materializedItem().macros();
    JsPreprocessingCompatService.JsResult result = jsPreprocessingCompatService.execute(value, scriptBody, macros);
    return switch (result.status()) {
      case "ok" -> StepResult.ok(result.value());
      case "discarded" -> new StepResult(value, "discarded", result.note(), null);
      default -> new StepResult(value, "error", result.note(), null);
    };
  }

  private StepResult applyMatchesRegex(String value, String regex, boolean expectedMatch) {
    boolean matched = Pattern.compile(regex).matcher(value).find();
    if (matched == expectedMatch) {
      return StepResult.ok(value);
    }
    String reason = (expectedMatch ? "MATCHES_REGEX" : "NOT_MATCHES_REGEX") + " validation failed";
    return new StepResult(value, "error", reason, null);
  }

  private StepResult applyInRange(String value, String parameter) {
    String[] split = parameter == null ? new String[0] : parameter.split(":");
    if (split.length < 2) {
      return new StepResult(value, "error", "IN_RANGE requires min:max", null);
    }
    double numeric = parseDouble(value, Double.NaN);
    if (Double.isNaN(numeric)) {
      return new StepResult(value, "error", "IN_RANGE value is not numeric", null);
    }
    double min = parseDouble(split[0], Double.NEGATIVE_INFINITY);
    double max = parseDouble(split[1], Double.POSITIVE_INFINITY);
    return (numeric >= min && numeric <= max)
        ? StepResult.ok(value)
        : new StepResult(value, "error", "IN_RANGE validation failed", null);
  }

  private StepResult applyJsonPath(String value, String jsonPath) throws Exception {
    String expression = jsonPath == null ? "" : jsonPath.trim();
    Matcher matcher = JSONPATH_PATTERN.matcher(expression);
    Matcher filteredMatcher = JSONPATH_FILTER_FIRST_PATTERN.matcher(expression);
    JsonNode root = OBJECT_MAPPER.readTree(value);
    if (matcher.matches()) {
      JsonNode node = root.get(matcher.group(1));
      if (node == null || node.isMissingNode() || node.isNull()) {
        return new StepResult(value, "error", "JSONPATH target not found", null);
      }
      return StepResult.ok(node.isValueNode() ? node.asText() : node.toString());
    }
    if (filteredMatcher.matches()) {
      if (!root.isArray()) {
        return new StepResult(value, "error", "JSONPATH filter expects array root", null);
      }
      String filterField = filteredMatcher.group(1);
      String filterValue = filteredMatcher.group(2);
      String targetField = filteredMatcher.group(3);
      for (JsonNode entry : root) {
        JsonNode fieldNode = entry.get(filterField);
        if (fieldNode != null && filterValue.equals(fieldNode.asText())) {
          JsonNode targetNode = entry.get(targetField);
          if (targetNode == null || targetNode.isMissingNode() || targetNode.isNull()) {
            return new StepResult(value, "error", "JSONPATH target not found", null);
          }
          return StepResult.ok(targetNode.isValueNode() ? targetNode.asText() : targetNode.toString());
        }
      }
      return new StepResult(value, "error", "JSONPATH filter result is empty", null);
    }
    return new StepResult(value, "error", "JSONPATH expression is not supported", null);
  }

  private StepResult applyXmlPath(String value, String xpath) throws Exception {
    Document doc = parseXml(value);
    String extracted = XPathFactory.newInstance()
        .newXPath()
        .evaluate(xpath, doc, XPathConstants.STRING)
        .toString();
    return StepResult.ok(extracted);
  }

  private StepResult checkJsonError(String value, String jsonPath) {
    if (jsonPath == null || jsonPath.isBlank()) {
      return StepResult.ok(value);
    }
    try {
      JsonNode root = OBJECT_MAPPER.readTree(value);
      String extracted = extractJsonPathValue(root, jsonPath);
      if (extracted != null && !extracted.isBlank()) {
        return new StepResult(value, "error", extracted.trim(), null);
      }
    } catch (Exception exception) {
      log.trace("CHECK_JSON_ERROR: invalid JSON, continuing: {}", exception.getMessage());
      return StepResult.ok(value);
    }
    return StepResult.ok(value);
  }

  private StepResult checkXmlError(String value, String xpath) {
    if (xpath == null || xpath.isBlank()) {
      return StepResult.ok(value);
    }
    try {
      Document doc = parseXml(value);
      String extracted = XPathFactory.newInstance()
          .newXPath()
          .evaluate(xpath, doc, XPathConstants.STRING)
          .toString();
      if (extracted != null && !extracted.isBlank()) {
        return new StepResult(value, "error", extracted.trim(), null);
      }
    } catch (Exception exception) {
      log.trace("CHECK_XML_ERROR: invalid XML, continuing: {}", exception.getMessage());
      return StepResult.ok(value);
    }
    return StepResult.ok(value);
  }

  private StepResult checkRegexError(String value, String regex, String outputTemplate) {
    if (regex == null || regex.isBlank()) {
      return new StepResult(value, "error", "CHECK_REGEX_ERROR requires pattern", null);
    }
    Matcher matcher = Pattern.compile(regex).matcher(value);
    if (matcher.find()) {
      String output = outputTemplate == null || outputTemplate.isBlank() ? "$0" : outputTemplate;
      String message = matcher.replaceFirst(output);
      if (!message.isBlank()) {
        return new StepResult(value, "error", message, null);
      }
    }
    return StepResult.ok(value);
  }

  private StepResult checkNotSupported(ZabbixPreprocessingStep step, String value) {
    if (!isUnsupportedValue(value)) {
      return StepResult.ok(value);
    }
    String scope = param(step, 0, "-1");
    String pattern = param(step, 1);
    boolean apply = switch (scope) {
      case "-1", "ANY_ERROR" -> true;
      case "0", "ERROR_MATCHES" -> !pattern.isBlank() && Pattern.compile(pattern).matcher(value).find();
      case "1", "ERROR_DOES_NOT_MATCH" -> pattern.isBlank() || !Pattern.compile(pattern).matcher(value).find();
      default -> true;
    };
    if (!apply) {
      return StepResult.ok(value);
    }
    return applyErrorHandler(step, value, "CHECK_NOT_SUPPORTED", "value is not supported");
  }

  private StepResult applyBooleanToDecimal(String value) {
    if (value == null) {
      return new StepResult("0", "error", "BOOL_TO_DECIMAL input is null", null);
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (TRUE_BOOLEAN_VALUES.contains(normalized)) {
      return StepResult.ok("1");
    }
    if (FALSE_BOOLEAN_VALUES.contains(normalized)) {
      return StepResult.ok("0");
    }
    double parsedNumeric = parseDouble(value, Double.NaN);
    if (!Double.isNaN(parsedNumeric)) {
      return StepResult.ok(parsedNumeric == 0.0d ? "0" : "1");
    }
    return new StepResult(value, "error", "BOOL_TO_DECIMAL value is not recognized", null);
  }

  /**
   * ifHighSpeed (Mbps) часто 0 на VM; ifSpeed (bps) уже в колонке walk 1.3.6.1.2.1.2.2.1.5.
   */
  private static String resolveIfSpeedWalkCell(
      List<String> columns,
      String oidWithMacros,
      JsonNode row,
      JsonNode primaryCell
  ) {
    String primary = primaryCell == null || primaryCell.isNull()
        ? null
        : (primaryCell.isValueNode() ? primaryCell.asText() : primaryCell.toString());
    if (!isIfHighSpeedWalkTarget(oidWithMacros) || !isBlankOrZeroSnmpValue(primary)) {
      return primary == null ? "" : primary;
    }
    String ifSpeed = readWalkRowCell(columns, row, OID_IF_SPEED);
    return isBlankOrZeroSnmpValue(ifSpeed) ? (primary == null ? "" : primary) : ifSpeed;
  }

  private static String readWalkRowCell(List<String> columns, JsonNode row, String columnOid) {
    int colIndex = columns.indexOf(columnOid);
    if (colIndex < 0) {
      return null;
    }
    String field = "col" + (colIndex + 1);
    JsonNode cell = row.get(field);
    if (cell == null || cell.isNull()) {
      return null;
    }
    return cell.isValueNode() ? cell.asText() : cell.toString();
  }

  private static boolean isIfHighSpeedWalkTarget(String oidWithMacros) {
    return oidWithMacros != null && oidWithMacros.contains(OID_IF_HIGH_SPEED);
  }

  private static boolean isBlankOrZeroSnmpValue(String value) {
    return value == null || value.isBlank() || "0".equals(value.trim());
  }

  private static boolean isNetIfSpeedItem(String itemKey) {
    return itemKey != null && itemKey.contains("net.if.speed");
  }

  private StepResult applyDiscardUnchangedWithHeartbeat(
      String value,
      ItemStateSnapshot previous,
      OffsetDateTime now,
      String heartbeatParam,
      MonitoringPreprocessContext ctx
  ) {
    if (ctx.materializedItem() != null && isNetIfSpeedItem(ctx.materializedItem().key())) {
      return StepResult.ok(value);
    }
    if (previous == null || previous.textValue() == null || !previous.textValue().equals(value)) {
      return StepResult.ok(value);
    }
    long heartbeatSeconds = parseSeconds(heartbeatParam, 0L);
    if (heartbeatSeconds <= 0L || previous.lastCollectedAt() == null) {
      return new StepResult(value, "discarded", "DISCARD_UNCHANGED_HEARTBEAT: unchanged value", null);
    }
    long elapsed = Math.max(0L, ChronoUnit.SECONDS.between(previous.lastCollectedAt(), now));
    if (elapsed < heartbeatSeconds) {
      return new StepResult(value, "discarded", "DISCARD_UNCHANGED_HEARTBEAT: unchanged within heartbeat", null);
    }
    return StepResult.ok(value);
  }

  private String extractJsonPathValue(JsonNode root, String jsonPath) {
    String expression = jsonPath == null ? "" : jsonPath.trim();
    Matcher matcher = JSONPATH_PATTERN.matcher(expression);
    Matcher filteredMatcher = JSONPATH_FILTER_FIRST_PATTERN.matcher(expression);
    if (matcher.matches()) {
      JsonNode node = root.get(matcher.group(1));
      if (node == null || node.isMissingNode() || node.isNull()) {
        return null;
      }
      return node.isValueNode() ? node.asText() : node.toString();
    }
    if (filteredMatcher.matches() && root.isArray()) {
      String filterField = filteredMatcher.group(1);
      String filterValue = filteredMatcher.group(2);
      String targetField = filteredMatcher.group(3);
      for (JsonNode entry : root) {
        JsonNode fieldNode = entry.get(filterField);
        if (fieldNode != null && filterValue.equals(fieldNode.asText())) {
          JsonNode targetNode = entry.get(targetField);
          if (targetNode == null || targetNode.isMissingNode() || targetNode.isNull()) {
            return null;
          }
          return targetNode.isValueNode() ? targetNode.asText() : targetNode.toString();
        }
      }
    }
    return null;
  }

  private boolean isUnsupportedValue(String value) {
    if (value == null || value.isBlank()) {
      return true;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    if (UNSUPPORTED_VALUE_TOKENS.contains(normalized)) {
      return true;
    }
    return normalized.contains("no such") || normalized.contains("timed out");
  }

  private long parseSeconds(String value, long fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    String normalized = value.trim().toLowerCase(Locale.ROOT);
    try {
      if (normalized.endsWith("ms")) {
        return Math.max(1L, Long.parseLong(normalized.substring(0, normalized.length() - 2)) / 1000L);
      }
      if (normalized.endsWith("s")) {
        return Long.parseLong(normalized.substring(0, normalized.length() - 1));
      }
      if (normalized.endsWith("m")) {
        return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 60L;
      }
      if (normalized.endsWith("h")) {
        return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 3600L;
      }
      if (normalized.endsWith("d")) {
        return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * 86400L;
      }
      return Long.parseLong(normalized);
    } catch (NumberFormatException exception) {
      return fallback;
    }
  }

  private StepResult xmlToJson(String value) throws Exception {
    Document doc = parseXml(value);
    JsonNode node = OBJECT_MAPPER.createObjectNode().put("xml", doc.getDocumentElement().getTextContent());
    return StepResult.ok(OBJECT_MAPPER.writeValueAsString(node));
  }

  private StepResult csvToJson(String value) throws Exception {
    String[] rows = value.split("\\R");
    if (rows.length < 2) {
      return new StepResult("[]", "fallback_applied", "CSV_TO_JSON input has no data rows", null);
    }
    String[] headers = rows[0].split(",");
    List<JsonNode> objects = new ArrayList<>();
    for (int i = 1; i < rows.length; i++) {
      String[] cols = rows[i].split(",", -1);
      var obj = OBJECT_MAPPER.createObjectNode();
      for (int c = 0; c < headers.length; c++) {
        String key = headers[c].trim();
        String colValue = c < cols.length ? cols[c].trim() : "";
        obj.put(key, colValue);
      }
      objects.add(obj);
    }
    return StepResult.ok(OBJECT_MAPPER.writeValueAsString(OBJECT_MAPPER.valueToTree(objects)));
  }

  private StepResult applyErrorHandler(ZabbixPreprocessingStep step, String value, String type, String details) {
    String handler = normalize(step.errorHandler());
    String info = (type + " failed: " + (details == null ? "unknown error" : details));
    if (handler.isBlank() || "ORIGINAL_ERROR".equals(handler)) {
      return new StepResult(value, "error", info, null);
    }
    if ("DISCARD_VALUE".equals(handler)) {
      return new StepResult(value, "discarded", info + " (discarded by handler)", null);
    }
    if ("CUSTOM_VALUE".equals(handler)) {
      return new StepResult(step.errorHandlerParams(), "fallback_applied", info + " (custom fallback)", null);
    }
    if ("SET_VALUE_TO_ERROR".equals(handler)) {
      return new StepResult("ERROR", "fallback_applied", info + " (set to ERROR)", null);
    }
    return new StepResult(value, "error", info + " (unsupported error handler: " + handler + ")", null);
  }

  private Document parseXml(String xml) throws Exception {
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setNamespaceAware(false);
    return factory.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
  }

  private String normalize(String value) {
    return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
  }

  private String param(ZabbixPreprocessingStep step, int index) {
    return param(step, index, "");
  }

  private String param(ZabbixPreprocessingStep step, int index, String fallback) {
    if (step.parameters() == null || step.parameters().size() <= index) {
      return fallback;
    }
    String value = step.parameters().get(index);
    return value == null ? fallback : value;
  }

  private String mergeNotes(String existing, String next) {
    if (next == null || next.isBlank()) {
      return existing;
    }
    if (existing == null || existing.isBlank()) {
      return next;
    }
    return existing + "; " + next;
  }

  private String trimLeft(String value, String chars) {
    if (chars == null || chars.isBlank()) {
      return value.stripLeading();
    }
    int index = 0;
    while (index < value.length() && chars.indexOf(value.charAt(index)) >= 0) {
      index++;
    }
    return value.substring(index);
  }

  private String trimRight(String value, String chars) {
    if (chars == null || chars.isBlank()) {
      return value.stripTrailing();
    }
    int index = value.length() - 1;
    while (index >= 0 && chars.indexOf(value.charAt(index)) >= 0) {
      index--;
    }
    return value.substring(0, index + 1);
  }

  private long parseHex(String value) {
    try {
      return Long.parseLong(value.replace("0x", "").replace("0X", ""), 16);
    } catch (NumberFormatException exception) {
      return 0L;
    }
  }

  private long parseOctal(String value) {
    try {
      return Long.parseLong(value, 8);
    } catch (NumberFormatException exception) {
      return 0L;
    }
  }

  private double parseDouble(String value, double fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    try {
      return Double.parseDouble(value);
    } catch (NumberFormatException exception) {
      java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(-?\\d+(?:\\.\\d+)?)").matcher(value);
      return matcher.find() ? Double.parseDouble(matcher.group(1)) : fallback;
    }
  }

  public record ProcessedMonitoringValue(
      Double numericValue,
      String textValue,
      boolean discarded,
      String status,
      String note
  ) {
  }

  private record StepResult(
      String value,
      String status,
      String note,
      /**
       * Если задано, в {@code monitoring_item_state.text_value} сохраняется это значение (база для SIMPLE_CHANGE / CHANGE_PER_SECOND),
       * а в UI/графики по-прежнему идёт {@link #value} через {@link ProcessedMonitoringValue#numericValue()}.
       */
      String itemStateTextOverride
  ) {
    private static StepResult ok(String value) {
      return new StepResult(value, "ok", null, null);
    }

    /**
     * {@code displayedValue} — результат шага для следующих шагов и numeric; {@code persistedText} — сырой счётчик в state.
     */
    private static StepResult okWithItemStateText(String displayedValue, String persistedText) {
      return new StepResult(displayedValue, "ok", null, persistedText);
    }

    private static StepResult fallback(String value, String note) {
      return new StepResult(value, "fallback_applied", note, null);
    }
  }
}
