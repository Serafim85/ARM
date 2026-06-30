package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixTrigger;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRuntime;
import com.networkscanner.backend.monitoring.util.ZabbixTemplateMacroSupport;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class TriggerEvaluationSupport {

  private static final Logger log = LoggerFactory.getLogger(TriggerEvaluationSupport.class);
  private static final Pattern FUNCTION_PATTERN =
      Pattern.compile("([a-z_][a-z0-9_]*)\\((.*)\\)", Pattern.CASE_INSENSITIVE);
  private static final Pattern TEMPLATE_MACRO_PATTERN = Pattern.compile("\\{\\$[^}]+}");
  private static final Pattern WINDOW_DURATION_PART_PATTERN = Pattern.compile("(\\d+)([smhdw])");
  private static final Set<String> LEGACY_FUNCTIONS =
      Set.of("avg", "min", "max", "last");
  private static final double INVALID_NUMERIC = Double.NaN;
  private static volatile boolean extendedFunctionsEnabled = true;
  private static final Cache<String, CompiledTriggerExpression> EXPRESSION_CACHE = Caffeine.newBuilder()
      .maximumSize(16_384)
      .expireAfterAccess(Duration.ofHours(2))
      .build();

  private TriggerEvaluationSupport() {
  }

  static List<MaterializedZabbixTrigger> materializeTriggers(
      ResolvedMonitoringTemplate template,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances
  ) {
    Map<String, String> templateMacros =
        template.templateMacros() == null ? Map.of() : template.templateMacros();
    List<MaterializedZabbixTrigger> triggers = new ArrayList<>();
    for (ZabbixTriggerRuntime trigger : template.triggers().values()) {
      if (!trigger.discoveryPrototype()) {
        triggers.add(new MaterializedZabbixTrigger(
            trigger,
            firstNonBlank(trigger.uuid(), trigger.expression()),
            "",
            trigger.expression(),
            blankToNull(trigger.recoveryExpression()),
            Set.copyOf(trigger.dependencyKeys() == null ? List.of() : trigger.dependencyKeys()),
            Map.of()
        ));
      }
    }
    for (ZabbixDiscoveryRuleRuntime rule : template.discoveryRules().values()) {
      List<DiscoveryInstanceRuntime> instances = discoveryInstances.getOrDefault(rule.key(), List.of());
      for (DiscoveryInstanceRuntime instance : instances) {
        for (ZabbixTriggerRuntime trigger : rule.triggerPrototypes()) {
          String expression = applyTemplateMacrosAfterLld(
              applyMacros(trigger.expression(), instance.macros()),
              templateMacros
          );
          String recoveryExpression = applyTemplateMacrosAfterLld(
              applyOptionalMacros(trigger.recoveryExpression(), instance.macros()),
              templateMacros
          );
          triggers.add(new MaterializedZabbixTrigger(
              trigger,
              firstNonBlank(trigger.uuid(), trigger.expression()),
              blankToEmpty(instance.instanceKey()),
              expression,
              recoveryExpression,
              materializeDependencies(trigger.dependencyKeys(), instance.macros()),
              instance.macros()
          ));
        }
      }
    }
    return deduplicateTriggersByMetricInstance(triggers);
  }

  static String metricInstanceKey(String metricName, String instanceKey) {
    return blankToEmpty(metricName) + ":" + blankToEmpty(instanceKey);
  }

  static String primaryMetricOfExpression(String expression) {
    if (expression == null || expression.isBlank()) {
      return "";
    }
    List<MetricWindowSpec> windows = compiled(expression).historyWindows();
    if (!windows.isEmpty()) {
      return firstNonBlank(windows.get(0).metricName(), "");
    }
    TriggerEvaluation evaluation = evaluateExpression(expression, OffsetDateTime.now(), (metricName, window, timestamp) -> List.of());
    return evaluation == null ? "" : firstNonBlank(evaluation.metricName(), "");
  }

  static List<MaterializedZabbixTrigger> deduplicateTriggersByMetricInstance(List<MaterializedZabbixTrigger> triggers) {
    if (triggers == null || triggers.isEmpty()) {
      return List.of();
    }
    if (triggers.size() == 1) {
      return List.copyOf(triggers);
    }
    Map<String, MaterializedZabbixTrigger> selected = new LinkedHashMap<>();
    for (MaterializedZabbixTrigger trigger : triggers) {
      String key = metricInstanceKey(
          primaryMetricOfExpression(trigger.expression()),
          trigger.instanceKey()
      );
      MaterializedZabbixTrigger existing = selected.get(key);
      if (existing == null
          || mapThresholdLevel(trigger.runtime().priority()).ordinal()
              > mapThresholdLevel(existing.runtime().priority()).ordinal()) {
        selected.put(key, trigger);
      }
    }
    return List.copyOf(selected.values());
  }

  static List<MetricHistoryRequest> collectHistoryRequests(
      ResolvedMonitoringTemplate template,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,
      OffsetDateTime timestamp
  ) {
    return collectHistoryRequestsForMaterialized(materializeTriggers(template, discoveryInstances), timestamp);
  }

  static List<MetricHistoryRequest> collectHistoryRequestsForMaterialized(
      List<MaterializedZabbixTrigger> materializedTriggers,
      OffsetDateTime timestamp
  ) {
    LinkedHashSet<MetricHistoryRequest> requests = new LinkedHashSet<>();
    for (MaterializedZabbixTrigger trigger : materializedTriggers) {
      for (MetricWindowSpec spec : compiled(trigger.expression()).historyWindows()) {
        requests.add(toHistoryRequest(spec.metricName(), spec.window(), timestamp));
      }
      if (trigger.recoveryExpression() != null && !trigger.recoveryExpression().isBlank()) {
        for (MetricWindowSpec spec : compiled(trigger.recoveryExpression()).historyWindows()) {
          requests.add(toHistoryRequest(spec.metricName(), spec.window(), timestamp));
        }
      }
    }
    return List.copyOf(requests);
  }

  static TriggerEvaluation evaluateExpression(
      String expression,
      OffsetDateTime timestamp,
      MetricWindowValueProvider valueProvider
  ) {
    String normalizedExpression = trim(expression);
    if (normalizedExpression.isBlank()) {
      return null;
    }
    if (containsUnresolvedTemplateMacro(normalizedExpression)) {
      log.warn("Skip trigger evaluation with unresolved template macro: {}", normalizedExpression);
      return null;
    }
    TriggerEvaluation evaluation = compiled(normalizedExpression).evaluate(timestamp, valueProvider);
    return evaluation != null && evaluation.valid() ? evaluation : null;
  }

  static double evaluateNumericExpression(
      String expression,
      OffsetDateTime timestamp,
      MetricWindowValueProvider valueProvider
  ) {
    return compileNumeric(trim(expression)).evaluate(timestamp, valueProvider);
  }

  /**
   * Извлекает все числовые сравнения из выражения триггера (включая ветки {@code and}/{@code or}).
   */
  static List<TriggerEvaluation> evaluateNumericComparisons(
      String expression,
      OffsetDateTime timestamp,
      MetricWindowValueProvider valueProvider
  ) {
    String normalizedExpression = trim(expression);
    if (normalizedExpression.isBlank() || containsUnresolvedTemplateMacro(normalizedExpression)) {
      return List.of();
    }
    return compiled(normalizedExpression).evaluateComparisons(timestamp, valueProvider).stream()
        .filter(evaluation -> evaluation != null
            && evaluation.valid()
            && !evaluation.metricName().isBlank()
            && Double.isFinite(evaluation.thresholdValue()))
        .toList();
  }

  /**
   * Числовое сравнение из expression триггера для линии порога на графике.
   * Правая часть может быть константой или формулой от метрик.
   */
  static final record ChartThresholdComparison(
      String metricName,
      String operator,
      double snapshotThresholdValue,
      boolean dynamic,
      NumericNode thresholdNode
  ) {
  }

  static final record ThresholdSample(OffsetDateTime recordedAt, double value) {
  }

  /**
   * Извлекает сравнения для визуализации порогов: не требует валидной левой части,
   * отсекает guard-условия ({@code last(speed)>0}) и нераскрытые макросы.
   */
  static List<ChartThresholdComparison> extractChartThresholdComparisons(
      String expression,
      OffsetDateTime timestamp,
      MetricWindowValueProvider valueProvider
  ) {
    String normalizedExpression = trim(expression);
    if (normalizedExpression.isBlank() || containsUnresolvedTemplateMacro(normalizedExpression)) {
      return List.of();
    }
    return compiled(normalizedExpression).extractChartThresholds(timestamp, valueProvider);
  }

  static List<String> historyMetricNamesForExpression(String expression) {
    String normalizedExpression = trim(expression);
    if (normalizedExpression.isBlank() || containsUnresolvedTemplateMacro(normalizedExpression)) {
      return List.of();
    }
    LinkedHashSet<String> names = new LinkedHashSet<>();
    for (MetricWindowSpec spec : compiled(normalizedExpression).historyWindows()) {
      if (spec.metricName() != null && !spec.metricName().isBlank()) {
        names.add(spec.metricName());
      }
    }
    return List.copyOf(names);
  }

  static List<ThresholdSample> evaluateThresholdSeries(
      ChartThresholdComparison comparison,
      List<OffsetDateTime> timeline,
      MetricWindowValueProvider valueProvider
  ) {
    if (comparison == null || timeline == null || timeline.isEmpty() || valueProvider == null) {
      return List.of();
    }
    List<ThresholdSample> samples = new ArrayList<>();
    for (OffsetDateTime at : timeline) {
      if (at == null) {
        continue;
      }
      double value = comparison.thresholdNode().evaluate(at, valueProvider);
      if (Double.isFinite(value)) {
        samples.add(new ThresholdSample(at, value));
      }
    }
    return List.copyOf(samples);
  }

  /**
   * Маппинг строки {@code priority} из шаблона Zabbix в {@link ThresholdLevel}.
   * Неизвестные и {@code null} трактуются как {@link ThresholdLevel#NOT_CLASSIFIED}.
   * В экспортах встречается {@code INFO} — приводим к {@link ThresholdLevel#INFORMATION}.
   */
  static ThresholdLevel mapThresholdLevel(String severity) {
    if (severity == null || severity.isBlank()) {
      return ThresholdLevel.NOT_CLASSIFIED;
    }
    return switch (severity.trim().toUpperCase()) {
      case "NOT_CLASSIFIED" -> ThresholdLevel.NOT_CLASSIFIED;
      case "INFORMATION", "INFO" -> ThresholdLevel.INFORMATION;
      case "WARNING" -> ThresholdLevel.WARNING;
      case "AVERAGE" -> ThresholdLevel.AVERAGE;
      case "HIGH" -> ThresholdLevel.HIGH;
      case "DISASTER" -> ThresholdLevel.DISASTER;
      default -> ThresholdLevel.NOT_CLASSIFIED;
    };
  }

  /**
   * Агрегация открытых уровней в три состояния здоровья устройства:
   * {@code HIGH} или {@code DISASTER} → {@link DeviceHealthStatus#CRITICAL};
   * иначе любой из остальных уровней → {@link DeviceHealthStatus#WARN};
   * пустой набор → {@link DeviceHealthStatus#NORM}.
   */
  static DeviceHealthStatus deriveHealthStatus(Collection<ThresholdLevel> levels) {
    if (levels == null || levels.isEmpty()) {
      return DeviceHealthStatus.NORM;
    }
    for (ThresholdLevel level : levels) {
      if (level == ThresholdLevel.HIGH || level == ThresholdLevel.DISASTER) {
        return DeviceHealthStatus.CRITICAL;
      }
    }
    return DeviceHealthStatus.WARN;
  }

  static String indexKey(String triggerUuid, String instanceKey, ThresholdLevel level) {
    return firstNonBlank(triggerUuid, "trigger") + ":" + blankToEmpty(instanceKey) + ":" + level.name();
  }

  static String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  static String blankToEmpty(String value) {
    return value == null ? "" : value;
  }

  static long cachedExpressionCount() {
    return EXPRESSION_CACHE.estimatedSize();
  }

  static void clearCaches() {
    EXPRESSION_CACHE.invalidateAll();
  }

  static void configureEngine(boolean extendedFunctionsEnabledValue) {
    extendedFunctionsEnabled = extendedFunctionsEnabledValue;
    clearCaches();
    log.info("Trigger expression engine configured: extendedFunctionsEnabled={}", extendedFunctionsEnabled);
  }

  static MetricHistoryRequest toHistoryRequest(String metricName, String window, OffsetDateTime timestamp) {
    if (window == null || window.isBlank()) {
      return new MetricHistoryRequest(metricName, null, 1);
    }
    String trimmed = window.trim().toLowerCase();
    if (trimmed.startsWith("#")) {
      try {
        return new MetricHistoryRequest(metricName, null, Integer.parseInt(trimmed.substring(1)));
      } catch (NumberFormatException exception) {
        log.warn("Invalid count history window '{}', fallback to last value", window);
        return new MetricHistoryRequest(metricName, null, 1);
      }
    }
    Long seconds = parseWindowSeconds(trimmed);
    if (seconds != null) {
      return new MetricHistoryRequest(metricName, timestamp.minusSeconds(seconds), null);
    }
    return new MetricHistoryRequest(metricName, null, 1);
  }

  static Long parseWindowSeconds(String window) {
    if (window == null || window.isBlank()) {
      return null;
    }
    String normalized = window.trim().toLowerCase(Locale.ROOT);
    if (normalized.chars().allMatch(Character::isDigit)) {
      try {
        return Long.parseLong(normalized);
      } catch (NumberFormatException ignored) {
        return null;
      }
    }
    long totalSeconds = 0L;
    int index = 0;
    while (index < normalized.length()) {
      while (index < normalized.length() && Character.isWhitespace(normalized.charAt(index))) {
        index++;
      }
      if (index >= normalized.length()) {
        break;
      }
      Matcher matcher = WINDOW_DURATION_PART_PATTERN.matcher(normalized.substring(index));
      if (!matcher.find() || matcher.start() != 0) {
        return null;
      }
      long chunk = Long.parseLong(matcher.group(1));
      totalSeconds += switch (matcher.group(2)) {
        case "s" -> chunk;
        case "m" -> chunk * 60L;
        case "h" -> chunk * 3600L;
        case "d" -> chunk * 86400L;
        case "w" -> chunk * 604800L;
        default -> 0L;
      };
      index += matcher.end();
    }
    return totalSeconds > 0 ? totalSeconds : null;
  }

  private static CompiledTriggerExpression compiled(String expression) {
    return EXPRESSION_CACHE.get(trim(expression), TriggerEvaluationSupport::compileExpression);
  }

  private static CompiledTriggerExpression compileExpression(String expression) {
    List<String> andParts = splitTopLevel(expression, "and");
    if (andParts.size() > 1) {
      return new LogicalExpression(
          andParts.stream().map(TriggerEvaluationSupport::compileExpression).toList(),
          true
      );
    }

    List<String> orParts = splitTopLevel(expression, "or");
    if (orParts.size() > 1) {
      return new LogicalExpression(
          orParts.stream().map(TriggerEvaluationSupport::compileExpression).toList(),
          false
      );
    }
    Comparison comparison = findComparison(expression);
    if (comparison != null && (comparison.left().contains("\"") || comparison.right().contains("\""))) {
      CompiledTriggerExpression stringComparison = compileStringComparison(comparison);
      if (stringComparison != null) {
        return stringComparison;
      }
    }
    if (comparison == null) {
      NumericNode node = compileNumeric(trim(expression));
      return new TruthyExpression(node);
    }
    return new ComparisonExpression(
        compileNumeric(comparison.left()),
        compileNumeric(comparison.right()),
        comparison.operator()
    );
  }

  private static NumericNode compileNumeric(String expression) {
    return new NumericParser(expression).parseExpression();
  }

  private static Comparison findComparison(String expression) {
    String value = trim(expression);
    int depth = 0;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '(') {
        depth++;
      } else if (ch == ')') {
        depth--;
      } else if (depth == 0) {
        String remainder = value.substring(i);
        for (String operator : List.of(">=", "<=", "<>", "=", ">", "<")) {
          if (remainder.startsWith(operator)) {
            return new Comparison(value.substring(0, i), operator, value.substring(i + operator.length()));
          }
        }
      }
    }
    return null;
  }

  private static boolean isChartThresholdOperator(String operator) {
    return operator != null && Set.of(">", "<", ">=", "<=", "=", "<>").contains(operator);
  }

  private static boolean isDynamicThreshold(NumericNode thresholdNode) {
    return !(thresholdNode instanceof ConstantNode);
  }

  /** Guard вроде {@code last(speed)>0} — не рисуем как линию порога. */
  private static boolean isGuardChartThreshold(NumericNode left, String operator, NumericNode right) {
    if (!(right instanceof ConstantNode constant) || constant.value() != 0.0d) {
      return false;
    }
    if (!">".equals(operator) && !">=".equals(operator)) {
      return false;
    }
    String metric = left.primaryMetric().toLowerCase(Locale.ROOT);
    return metric.contains("speed") || metric.contains("status") || metric.contains("operstatus");
  }

  private static boolean compare(double left, double right, String operator) {
    return switch (operator) {
      case "=" -> Double.compare(left, right) == 0;
      case "<>" -> Double.compare(left, right) != 0;
      case ">" -> left > right;
      case "<" -> left < right;
      case ">=" -> left >= right;
      case "<=" -> left <= right;
      default -> false;
    };
  }

  private static ParsedMetricReference parseMetricReference(String rawArgument) {
    String value = trim(unquote(rawArgument));
    if (!value.startsWith("/")) {
      return null;
    }
    if (value.startsWith("//")) {
      String metric = value.substring(2).trim();
      return metric.isEmpty() ? null : new ParsedMetricReference(metric);
    }
    int firstSlash = value.indexOf('/');
    int secondSlash = value.indexOf('/', firstSlash + 1);
    if (secondSlash < 0 || secondSlash == value.length() - 1) {
      return null;
    }
    return new ParsedMetricReference(value.substring(secondSlash + 1).trim());
  }

  private static List<String> splitFunctionArguments(String arguments) {
    if (arguments == null || arguments.isBlank()) {
      return List.of();
    }
    List<String> parts = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int roundDepth = 0;
    int squareDepth = 0;
    boolean inQuotes = false;
    for (int i = 0; i < arguments.length(); i++) {
      char ch = arguments.charAt(i);
      if (ch == '"' && (i == 0 || arguments.charAt(i - 1) != '\\')) {
        inQuotes = !inQuotes;
      }
      if (!inQuotes) {
        if (ch == '(') {
          roundDepth++;
        } else if (ch == ')') {
          roundDepth--;
        } else if (ch == '[') {
          squareDepth++;
        } else if (ch == ']') {
          squareDepth--;
        } else if (ch == ',' && roundDepth == 0 && squareDepth == 0) {
          parts.add(current.toString().trim());
          current.setLength(0);
          continue;
        }
      }
      current.append(ch);
    }
    parts.add(current.toString().trim());
    return parts;
  }

  private static String unquote(String value) {
    if (value == null) {
      return "";
    }
    String trimmed = value.trim();
    if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
      return trimmed.substring(1, trimmed.length() - 1);
    }
    return trimmed;
  }

  private static boolean isQuotedLiteral(String value) {
    if (value == null) {
      return false;
    }
    String trimmed = value.trim();
    return trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"");
  }

  private static CompiledTriggerExpression compileStringComparison(Comparison comparison) {
    StringExpressionNode left = compileStringNode(comparison.left());
    StringExpressionNode right = compileStringNode(comparison.right());
    if (left == null || right == null) {
      return null;
    }
    if (!"=".equals(comparison.operator()) && !"<>".equals(comparison.operator())) {
      return null;
    }
    return new StringComparisonExpression(left, right, comparison.operator());
  }

  private static StringExpressionNode compileStringNode(String operand) {
    String trimmed = trim(operand);
    if (isQuotedLiteral(trimmed)) {
      return new StringLiteralNode(unquote(trimmed));
    }
    Matcher matcher = FUNCTION_PATTERN.matcher(trimmed);
    if (matcher.matches()) {
      String fn = matcher.group(1).toLowerCase(Locale.ROOT);
      if (!"last".equals(fn)) {
        return null;
      }
      List<String> args = splitFunctionArguments(matcher.group(2));
      if (args.isEmpty()) {
        return null;
      }
      ParsedMetricReference ref = parseMetricReference(args.get(0));
      if (ref == null) {
        return null;
      }
      return new LastTextFunctionNode(ref.metricKey());
    }
    return null;
  }

  private record ParsedMetricReference(String metricKey) {
  }

  private static List<String> splitTopLevel(String expression, String delimiter) {
    List<String> result = new ArrayList<>();
    StringBuilder current = new StringBuilder();
    int depth = 0;
    String lower = expression.toLowerCase();
    for (int i = 0; i < expression.length(); i++) {
      char ch = expression.charAt(i);
      if (ch == '(') {
        depth++;
      } else if (ch == ')') {
        depth--;
      }
      if (depth == 0 && isLogicalDelimiterAt(lower, i, delimiter)) {
        result.add(current.toString());
        current.setLength(0);
        i = logicalDelimiterEndIndex(lower, i, delimiter);
        continue;
      }
      current.append(ch);
    }
    result.add(current.toString());
    return result.stream().map(TriggerEvaluationSupport::trim).toList();
  }

  private static boolean isLogicalDelimiterAt(String lowerExpression, int index, String delimiter) {
    if (index <= 0 || index >= lowerExpression.length()) {
      return false;
    }
    if (!Character.isWhitespace(lowerExpression.charAt(index - 1))) {
      return false;
    }
    int tokenStart = index;
    while (tokenStart < lowerExpression.length() && Character.isWhitespace(lowerExpression.charAt(tokenStart))) {
      tokenStart++;
    }
    if (tokenStart + delimiter.length() > lowerExpression.length()) {
      return false;
    }
    if (!lowerExpression.startsWith(delimiter, tokenStart)) {
      return false;
    }
    int tokenEnd = tokenStart + delimiter.length();
    return tokenEnd < lowerExpression.length() && Character.isWhitespace(lowerExpression.charAt(tokenEnd));
  }

  private static int logicalDelimiterEndIndex(String lowerExpression, int index, String delimiter) {
    int tokenStart = index;
    while (tokenStart < lowerExpression.length() && Character.isWhitespace(lowerExpression.charAt(tokenStart))) {
      tokenStart++;
    }
    int tokenEnd = tokenStart + delimiter.length();
    int trailing = tokenEnd;
    while (trailing < lowerExpression.length() && Character.isWhitespace(lowerExpression.charAt(trailing))) {
      trailing++;
    }
    return trailing - 1;
  }

  private static String trim(String value) {
    String trimmed = value == null ? "" : value.trim();
    while (trimmed.startsWith("(") && trimmed.endsWith(")") && isWrapped(trimmed)) {
      trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
    }
    return trimmed;
  }

  private static boolean containsUnresolvedTemplateMacro(String value) {
    return ZabbixTemplateMacroSupport.containsUnresolvedTemplateMacroReference(value);
  }

  private static String applyTemplateMacrosAfterLld(String value, Map<String, String> templateMacros) {
    if (value == null || value.isBlank() || templateMacros == null || templateMacros.isEmpty()) {
      return value;
    }
    return ZabbixTemplateMacroSupport.applyTemplateMacros(value, templateMacros);
  }

  private static boolean isWrapped(String value) {
    int depth = 0;
    for (int i = 0; i < value.length(); i++) {
      char ch = value.charAt(i);
      if (ch == '(') {
        depth++;
      } else if (ch == ')') {
        depth--;
        if (depth == 0 && i < value.length() - 1) {
          return false;
        }
      }
    }
    return true;
  }

  private static String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static String applyMacros(String value, Map<String, String> macros) {
    if (value == null || value.isBlank()) {
      return "";
    }
    String result = value;
    for (Map.Entry<String, String> macro : macros.entrySet()) {
      result = result.replace(macro.getKey(), macro.getValue());
    }
    return result;
  }

  private static String applyOptionalMacros(String value, Map<String, String> macros) {
    if (value == null || value.isBlank()) {
      return null;
    }
    return applyMacros(value, macros);
  }

  private static Set<String> materializeDependencies(List<String> dependencyKeys, Map<String, String> macros) {
    if (dependencyKeys == null || dependencyKeys.isEmpty()) {
      return Set.of();
    }
    return dependencyKeys.stream()
        .map(value -> applyMacros(value, macros))
        .map(TriggerEvaluationSupport::trim)
        .filter(value -> !value.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
  }

  interface MetricWindowValueProvider {
    List<Double> loadMetricValues(String metricName, String window, OffsetDateTime timestamp);

    default String loadLatestTextValue(String metricName, OffsetDateTime timestamp) {
      return null;
    }
  }

  record TriggerEvaluation(
      String metricName,
      double actualValue,
      double thresholdValue,
      boolean breached,
      boolean valid,
      String operator
  ) {
  }

  private record Comparison(String left, String operator, String right) {
  }

  private static class NumericParser {
    private final String input;
    private int index = 0;

    private NumericParser(String input) {
      this.input = input;
    }

    private NumericNode parseExpression() {
      NumericNode value = parseTerm();
      while (true) {
        skipWhitespace();
        if (peek('+')) {
          index++;
          value = new BinaryOperationNode(value, parseTerm(), '+');
        } else if (peek('-')) {
          index++;
          value = new BinaryOperationNode(value, parseTerm(), '-');
        } else {
          break;
        }
      }
      return value;
    }

    private NumericNode parseTerm() {
      NumericNode value = parseFactor();
      while (true) {
        skipWhitespace();
        if (peek('*')) {
          index++;
          value = new BinaryOperationNode(value, parseFactor(), '*');
        } else if (peek('/')) {
          index++;
          value = new BinaryOperationNode(value, parseFactor(), '/');
        } else {
          break;
        }
      }
      return value;
    }

    private NumericNode parseFactor() {
      skipWhitespace();
      if (peek('(')) {
        index++;
        NumericNode value = parseExpression();
        skipWhitespace();
        if (peek(')')) {
          index++;
        }
        return value;
      }
      String token = readToken();
      if (token == null || token.isBlank()) {
        return invalidConstant("empty token", token);
      }
      Matcher matcher = FUNCTION_PATTERN.matcher(token);
      if (matcher.matches()) {
        return new FunctionNode(
            matcher.group(1).toLowerCase(Locale.ROOT),
            splitFunctionArguments(matcher.group(2))
        );
      }
      try {
        return new ConstantNode(Double.parseDouble(token));
      } catch (NumberFormatException exception) {
        return invalidConstant("non-numeric token", token);
      }
    }

    private NumericNode invalidConstant(String reason, String token) {
      log.warn("Failed to parse trigger expression segment ({}) in '{}', token='{}'", reason, input, token);
      return new ConstantNode(INVALID_NUMERIC);
    }

    private String readToken() {
      skipWhitespace();
      int start = index;
      int depth = 0;
      while (index < input.length()) {
        char ch = input.charAt(index);
        if (ch == '(') {
          depth++;
        } else if (ch == ')') {
          if (depth == 0) {
            break;
          }
          depth--;
        } else if (depth == 0 && (ch == '+' || ch == '-' || ch == '*' || ch == '/')) {
          break;
        }
        index++;
      }
      return input.substring(start, index).trim();
    }

    private boolean peek(char ch) {
      return index < input.length() && input.charAt(index) == ch;
    }

    private void skipWhitespace() {
      while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
        index++;
      }
    }
  }

  private interface CompiledTriggerExpression {
    TriggerEvaluation evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider);

    default List<TriggerEvaluation> evaluateComparisons(
        OffsetDateTime timestamp,
        MetricWindowValueProvider valueProvider
    ) {
      TriggerEvaluation single = evaluate(timestamp, valueProvider);
      if (single == null || !single.valid()) {
        return List.of();
      }
      return List.of(single);
    }

    default List<ChartThresholdComparison> extractChartThresholds(
        OffsetDateTime timestamp,
        MetricWindowValueProvider valueProvider
    ) {
      return List.of();
    }

    List<MetricWindowSpec> historyWindows();
  }

  private interface NumericNode {
    double evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider);

    List<MetricWindowSpec> historyWindows();

    String primaryMetric();
  }

  private interface StringExpressionNode {
    String evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider);

    String primaryMetric();
  }

  private record LogicalExpression(
      List<CompiledTriggerExpression> children,
      boolean andOperator
  ) implements CompiledTriggerExpression {
    @Override
    public TriggerEvaluation evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      boolean breached = andOperator;
      TriggerEvaluation primary = null;
      for (CompiledTriggerExpression child : children) {
        TriggerEvaluation current = child.evaluate(timestamp, valueProvider);
        if (current == null) {
          return null;
        }
        if (!current.valid()) {
          return current;
        }
        if (primary == null) {
          primary = current;
        }
        breached = andOperator ? breached && current.breached() : breached || current.breached();
      }
      return primary == null ? null
          : new TriggerEvaluation(
              primary.metricName(),
              primary.actualValue(),
              primary.thresholdValue(),
              breached,
              true,
              primary.operator()
          );
    }

    @Override
    public List<TriggerEvaluation> evaluateComparisons(
        OffsetDateTime timestamp,
        MetricWindowValueProvider valueProvider
    ) {
      List<TriggerEvaluation> comparisons = new ArrayList<>();
      for (CompiledTriggerExpression child : children) {
        comparisons.addAll(child.evaluateComparisons(timestamp, valueProvider));
      }
      return comparisons;
    }

    @Override
    public List<ChartThresholdComparison> extractChartThresholds(
        OffsetDateTime timestamp,
        MetricWindowValueProvider valueProvider
    ) {
      List<ChartThresholdComparison> comparisons = new ArrayList<>();
      for (CompiledTriggerExpression child : children) {
        comparisons.addAll(child.extractChartThresholds(timestamp, valueProvider));
      }
      return comparisons;
    }

    @Override
    public List<MetricWindowSpec> historyWindows() {
      LinkedHashSet<MetricWindowSpec> windows = new LinkedHashSet<>();
      for (CompiledTriggerExpression child : children) {
        windows.addAll(child.historyWindows());
      }
      return List.copyOf(windows);
    }
  }

  private record ComparisonExpression(
      NumericNode left,
      NumericNode right,
      String operator
  ) implements CompiledTriggerExpression {
    @Override
    public TriggerEvaluation evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      double leftValue = left.evaluate(timestamp, valueProvider);
      double rightValue = right.evaluate(timestamp, valueProvider);
      boolean valid = !Double.isNaN(leftValue) && !Double.isNaN(rightValue);
      return new TriggerEvaluation(
          firstNonBlank(left.primaryMetric(), ""),
          leftValue,
          rightValue,
          valid && compare(leftValue, rightValue, operator),
          valid,
          operator
      );
    }

    @Override
    public List<ChartThresholdComparison> extractChartThresholds(
        OffsetDateTime timestamp,
        MetricWindowValueProvider valueProvider
    ) {
      if (!isChartThresholdOperator(operator)) {
        return List.of();
      }
      double rightValue = right.evaluate(timestamp, valueProvider);
      if (!Double.isFinite(rightValue)) {
        return List.of();
      }
      String metricName = firstNonBlank(left.primaryMetric(), "");
      if (metricName.isBlank() || isGuardChartThreshold(left, operator, right)) {
        return List.of();
      }
      return List.of(new ChartThresholdComparison(
          metricName,
          operator,
          rightValue,
          isDynamicThreshold(right),
          right
      ));
    }

    @Override
    public List<MetricWindowSpec> historyWindows() {
      LinkedHashSet<MetricWindowSpec> windows = new LinkedHashSet<>(left.historyWindows());
      windows.addAll(right.historyWindows());
      return List.copyOf(windows);
    }
  }

  private record TruthyExpression(NumericNode node) implements CompiledTriggerExpression {
    @Override
    public TriggerEvaluation evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      double value = node.evaluate(timestamp, valueProvider);
      boolean valid = !Double.isNaN(value);
      return new TriggerEvaluation(firstNonBlank(node.primaryMetric(), ""), value, 0.0, valid && value > 0, valid, "");
    }

    @Override
    public List<TriggerEvaluation> evaluateComparisons(
        OffsetDateTime timestamp,
        MetricWindowValueProvider valueProvider
    ) {
      return List.of();
    }

    @Override
    public List<MetricWindowSpec> historyWindows() {
      return node.historyWindows();
    }
  }

  private record StringComparisonExpression(
      StringExpressionNode left,
      StringExpressionNode right,
      String operator
  ) implements CompiledTriggerExpression {
    @Override
    public TriggerEvaluation evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      String leftValue = left.evaluate(timestamp, valueProvider);
      String rightValue = right.evaluate(timestamp, valueProvider);
      boolean valid = leftValue != null && rightValue != null;
      boolean breached = valid && switch (operator) {
        case "=" -> leftValue.equals(rightValue);
        case "<>" -> !leftValue.equals(rightValue);
        default -> false;
      };
      return new TriggerEvaluation(
          firstNonBlank(left.primaryMetric(), right.primaryMetric()),
          breached ? 1.0d : 0.0d,
          0.0d,
          breached,
          valid,
          operator
      );
    }

    @Override
    public List<TriggerEvaluation> evaluateComparisons(
        OffsetDateTime timestamp,
        MetricWindowValueProvider valueProvider
    ) {
      return List.of();
    }

    @Override
    public List<MetricWindowSpec> historyWindows() {
      return List.of();
    }
  }

  private record ConstantNode(double value) implements NumericNode {
    @Override
    public double evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      return value;
    }

    @Override
    public List<MetricWindowSpec> historyWindows() {
      return List.of();
    }

    @Override
    public String primaryMetric() {
      return "";
    }
  }

  private record BinaryOperationNode(
      NumericNode left,
      NumericNode right,
      char operator
  ) implements NumericNode {
    @Override
    public double evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      double leftValue = left.evaluate(timestamp, valueProvider);
      double rightValue = right.evaluate(timestamp, valueProvider);
      return switch (operator) {
        case '+' -> leftValue + rightValue;
        case '-' -> leftValue - rightValue;
        case '*' -> leftValue * rightValue;
        case '/' -> leftValue / rightValue;
        default -> 0.0;
      };
    }

    @Override
    public List<MetricWindowSpec> historyWindows() {
      LinkedHashSet<MetricWindowSpec> windows = new LinkedHashSet<>(left.historyWindows());
      windows.addAll(right.historyWindows());
      return List.copyOf(windows);
    }

    @Override
    public String primaryMetric() {
      return firstNonBlank(left.primaryMetric(), right.primaryMetric());
    }
  }

  private record StringLiteralNode(String value) implements StringExpressionNode {
    @Override
    public String evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      return value;
    }

    @Override
    public String primaryMetric() {
      return "";
    }
  }

  private record LastTextFunctionNode(String metricName) implements StringExpressionNode {
    @Override
    public String evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      return valueProvider.loadLatestTextValue(metricName, timestamp);
    }

    @Override
    public String primaryMetric() {
      return metricName;
    }
  }

  private record FunctionNode(
      String function,
      List<String> args
  ) implements NumericNode {
    @Override
    public double evaluate(OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      String normalized = function.toLowerCase(Locale.ROOT);
      if (!extendedFunctionsEnabled && !LEGACY_FUNCTIONS.contains(normalized)) {
        return Double.NaN;
      }
      ParsedMetricReference metricRef = metricArg(0);
      String window = optionalArg(1);
      List<Double> values = metricRef == null ? List.of() : valueProvider.loadMetricValues(metricRef.metricKey(), window, timestamp);
      return switch (normalized) {
        case "last" -> at(values, 0);
        case "first" -> at(values, values.size() - 1);
        case "min", "trendmin" -> values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        case "max", "trendmax" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN);
        case "avg", "trendavg" -> values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        case "sum", "trendsum" -> values.stream().mapToDouble(Double::doubleValue).sum();
        case "count", "trendcount" -> values.size();
        case "median" -> median(values);
        case "stddevpop" -> stddev(values, false);
        case "stddevsamp" -> stddev(values, true);
        case "delta" -> values.stream().mapToDouble(Double::doubleValue).max().orElse(Double.NaN)
            - values.stream().mapToDouble(Double::doubleValue).min().orElse(Double.NaN);
        case "change" -> values.size() < 2 ? Double.NaN : values.get(0) - values.get(values.size() - 1);
        case "nodata" -> values.isEmpty() ? 1.0 : 0.0;
        case "timeleft" -> timeleft(values, numericArg(2, timestamp, valueProvider), window);
        case "percentile" -> percentile(values, numericArgOrDefault(2, timestamp, valueProvider, 95.0d));
        case "strlen" -> (double) unquote(optionalArg(0) == null ? "" : optionalArg(0)).length();
        case "abs" -> Math.abs(numericArg(0, timestamp, valueProvider));
        case "ceil" -> Math.ceil(numericArg(0, timestamp, valueProvider));
        case "floor" -> Math.floor(numericArg(0, timestamp, valueProvider));
        case "round" -> round(numericArg(0, timestamp, valueProvider), numericArgOrDefault(1, timestamp, valueProvider, 0.0));
        case "trunc" -> trunc(numericArg(0, timestamp, valueProvider), numericArgOrDefault(1, timestamp, valueProvider, 0.0));
        case "sqrt" -> Math.sqrt(numericArg(0, timestamp, valueProvider));
        case "sin" -> Math.sin(numericArg(0, timestamp, valueProvider));
        case "cos" -> Math.cos(numericArg(0, timestamp, valueProvider));
        case "tan" -> Math.tan(numericArg(0, timestamp, valueProvider));
        case "asin" -> Math.asin(numericArg(0, timestamp, valueProvider));
        case "acos" -> Math.acos(numericArg(0, timestamp, valueProvider));
        case "atan" -> Math.atan(numericArg(0, timestamp, valueProvider));
        case "exp" -> Math.exp(numericArg(0, timestamp, valueProvider));
        case "ln" -> Math.log(numericArg(0, timestamp, valueProvider));
        case "log" -> Math.log(numericArg(0, timestamp, valueProvider));
        case "log10" -> Math.log10(numericArg(0, timestamp, valueProvider));
        case "pow" -> Math.pow(
            numericArg(0, timestamp, valueProvider),
            numericArg(1, timestamp, valueProvider)
        );
        case "now" -> timestamp.toEpochSecond();
        case "time" -> timestamp.getHour() * 10000 + timestamp.getMinute() * 100 + timestamp.getSecond();
        case "date" -> timestamp.getYear() * 10000 + timestamp.getMonthValue() * 100 + timestamp.getDayOfMonth();
        case "dayofweek" -> timestamp.getDayOfWeek().getValue();
        case "dayofmonth" -> timestamp.getDayOfMonth();
        case "dayofyear" -> timestamp.getDayOfYear();
        case "month" -> timestamp.getMonthValue();
        case "year" -> timestamp.getYear();
        case "hour" -> timestamp.getHour();
        case "minute" -> timestamp.getMinute();
        case "second" -> timestamp.getSecond();
        default -> {
          log.warn("Unsupported Zabbix function '{}', args={}", function, args);
          yield Double.NaN;
        }
      };
    }

    @Override
    public List<MetricWindowSpec> historyWindows() {
      ParsedMetricReference ref = metricArg(0);
      if (ref == null) {
        return List.of();
      }
      return List.of(new MetricWindowSpec(ref.metricKey(), optionalArg(1)));
    }

    @Override
    public String primaryMetric() {
      ParsedMetricReference ref = metricArg(0);
      return ref == null ? "" : ref.metricKey();
    }

    private ParsedMetricReference metricArg(int index) {
      if (index < 0 || index >= args.size()) {
        return null;
      }
      return parseMetricReference(args.get(index));
    }

    private String optionalArg(int index) {
      if (index < 0 || index >= args.size()) {
        return null;
      }
      String value = unquote(args.get(index));
      return value.isBlank() ? null : value;
    }

    private double numericArg(int index, OffsetDateTime timestamp, MetricWindowValueProvider valueProvider) {
      if (index < 0 || index >= args.size()) {
        return Double.NaN;
      }
      return compileNumeric(trim(args.get(index))).evaluate(timestamp, valueProvider);
    }

    private double numericArgOrDefault(
        int index,
        OffsetDateTime timestamp,
        MetricWindowValueProvider valueProvider,
        double fallback
    ) {
      double value = numericArg(index, timestamp, valueProvider);
      return Double.isNaN(value) ? fallback : value;
    }

    private double at(List<Double> values, int index) {
      if (values.isEmpty() || index < 0 || index >= values.size()) {
        return Double.NaN;
      }
      return values.get(index);
    }

    private double median(List<Double> values) {
      if (values.isEmpty()) {
        return Double.NaN;
      }
      List<Double> sorted = new ArrayList<>(values);
      sorted.sort(Double::compareTo);
      int mid = sorted.size() / 2;
      if (sorted.size() % 2 == 0) {
        return (sorted.get(mid - 1) + sorted.get(mid)) / 2.0d;
      }
      return sorted.get(mid);
    }

    private double stddev(List<Double> values, boolean sample) {
      if (values.size() < (sample ? 2 : 1)) {
        return Double.NaN;
      }
      double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
      if (Double.isNaN(mean)) {
        return Double.NaN;
      }
      double variance = values.stream()
          .mapToDouble(v -> Math.pow(v - mean, 2))
          .sum() / (sample ? (values.size() - 1.0d) : values.size());
      return Math.sqrt(variance);
    }

    private double percentile(List<Double> values, double percentile) {
      if (values.isEmpty()) {
        return Double.NaN;
      }
      List<Double> sorted = new ArrayList<>(values);
      sorted.sort(Double::compareTo);
      double rank = (Math.max(0.0d, Math.min(100.0d, percentile)) / 100.0d) * (sorted.size() - 1);
      int lower = (int) Math.floor(rank);
      int upper = (int) Math.ceil(rank);
      if (lower == upper) {
        return sorted.get(lower);
      }
      double weight = rank - lower;
      return sorted.get(lower) * (1.0d - weight) + sorted.get(upper) * weight;
    }

    private double timeleft(List<Double> values, double threshold, String window) {
      if (values.size() < 2 || Double.isNaN(threshold)) {
        return Double.NaN;
      }
      double newest = values.get(0);
      double oldest = values.get(values.size() - 1);
      Long seconds = parseWindowSeconds(window);
      double timeSpan = seconds == null ? values.size() - 1.0d : seconds;
      if (timeSpan <= 0.0d) {
        return Double.NaN;
      }
      double slope = (newest - oldest) / timeSpan;
      if (slope <= 0.0d) {
        return Double.POSITIVE_INFINITY;
      }
      return Math.max((threshold - newest) / slope, 0.0d);
    }

    private double round(double value, double precision) {
      if (Double.isNaN(value)) {
        return value;
      }
      int scale = (int) precision;
      double factor = Math.pow(10, Math.max(scale, 0));
      return Math.round(value * factor) / factor;
    }

    private double trunc(double value, double precision) {
      if (Double.isNaN(value)) {
        return value;
      }
      int scale = (int) precision;
      double factor = Math.pow(10, Math.max(scale, 0));
      return Math.floor(value * factor) / factor;
    }
  }

  private record MetricWindowSpec(String metricName, String window) {
  }
}
