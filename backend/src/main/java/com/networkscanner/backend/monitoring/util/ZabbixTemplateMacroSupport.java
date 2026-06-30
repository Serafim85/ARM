package com.networkscanner.backend.monitoring.util;

import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryConditionRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryFilterRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixMacroRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTemplateRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRuntime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ZabbixTemplateMacroSupport {

  private static final Logger log = LoggerFactory.getLogger(ZabbixTemplateMacroSupport.class);

  private static final Pattern TEMPLATE_CONTEXTUAL_MACRO =
      Pattern.compile("^\\{\\$([A-Za-z0-9_.]+):.*}$");

  private ZabbixTemplateMacroSupport() {
  }

  public static Map<String, String> compileMacros(List<ZabbixMacroRecord> macros) {
    Map<String, String> compiled = new LinkedHashMap<>();
    if (macros == null) {
      return Map.of();
    }
    for (ZabbixMacroRecord macro : macros) {
      if (macro == null || macro.macro() == null || macro.macro().isBlank() || macro.value() == null) {
        continue;
      }
      compiled.put(macro.macro(), macro.value());
    }
    return Map.copyOf(compiled);
  }

  public static Map<String, String> mergeMacroMaps(Map<String, String> base, Map<String, String> overlay) {
    if (base == null || base.isEmpty()) {
      return overlay == null ? Map.of() : Map.copyOf(overlay);
    }
    Map<String, String> merged = new LinkedHashMap<>(base);
    if (overlay != null) {
      merged.putAll(overlay);
    }
    return Map.copyOf(merged);
  }

  public static Map<String, String> collectLinkedTemplateMacros(
      ZabbixTemplateRecord template,
      List<ZabbixTemplateRecord> allTemplates
  ) {
    return collectLinkedTemplateMacros(template, allTemplates, new LinkedHashSet<>());
  }

  private static Map<String, String> collectLinkedTemplateMacros(
      ZabbixTemplateRecord template,
      List<ZabbixTemplateRecord> allTemplates,
      Set<String> visitedTemplateNames
  ) {
    if (template == null || template.templates() == null || template.templates().isEmpty()) {
      return Map.of();
    }
    Map<String, String> collected = new LinkedHashMap<>();
    for (var link : template.templates()) {
      if (link == null || link.name() == null || link.name().isBlank()) {
        continue;
      }
      String linkedName = link.name().trim();
      if (!visitedTemplateNames.add(linkedName)) {
        continue;
      }
      ZabbixTemplateRecord linked = findTemplateByName(allTemplates, linkedName);
      if (linked == null) {
        log.debug("Linked template not found in export: {}", linkedName);
        continue;
      }
      collected.putAll(compileMacros(linked.macros()));
      collected.putAll(collectLinkedTemplateMacros(linked, allTemplates, visitedTemplateNames));
    }
    return Map.copyOf(collected);
  }

  private static ZabbixTemplateRecord findTemplateByName(List<ZabbixTemplateRecord> templates, String name) {
    if (templates == null || name == null) {
      return null;
    }
    for (ZabbixTemplateRecord candidate : templates) {
      if (candidate == null) {
        continue;
      }
      if (name.equals(candidate.template()) || name.equals(candidate.name())) {
        return candidate;
      }
    }
    return null;
  }

  public static String applyTemplateMacros(String value, Map<String, String> templateMacros) {
    if (value == null || value.isBlank() || templateMacros == null || templateMacros.isEmpty()) {
      return value;
    }
    List<MacroSpan> references = findTemplateMacroReferences(value);
    if (references.isEmpty()) {
      return value;
    }
    StringBuilder resolved = new StringBuilder();
    int cursor = 0;
    for (MacroSpan reference : references) {
      resolved.append(value, cursor, reference.startIndex());
      String macroReference = reference.value();
      String replacement = resolveTemplateMacroValue(macroReference, templateMacros);
      resolved.append(replacement == null ? macroReference : replacement);
      cursor = reference.endIndex();
    }
    resolved.append(value.substring(cursor));
    return resolved.toString();
  }

  public static boolean containsUnresolvedTemplateMacroReference(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    return !findTemplateMacroReferences(value).isEmpty();
  }

  public static String resolveTemplateMacroValue(String macroReference, Map<String, String> templateMacros) {
    if (macroReference == null || macroReference.isBlank() || templateMacros == null || templateMacros.isEmpty()) {
      return null;
    }
    String exactValue = templateMacros.get(macroReference);
    if (exactValue != null) {
      return exactValue;
    }
    String baseMacro = contextualMacroBase(macroReference);
    if (baseMacro == null) {
      return null;
    }
    return templateMacros.get(baseMacro);
  }

  public static String contextualMacroBase(String macroReference) {
    Matcher matcher = TEMPLATE_CONTEXTUAL_MACRO.matcher(macroReference);
    if (!matcher.matches()) {
      return null;
    }
    return "{$" + matcher.group(1) + "}";
  }

  public static List<ZabbixTriggerRuntime> resolveTriggerList(
      List<ZabbixTriggerRuntime> triggers,
      Map<String, String> templateMacros
  ) {
    if (triggers == null || triggers.isEmpty()) {
      return List.of();
    }
    List<ZabbixTriggerRuntime> resolved = new ArrayList<>();
    for (ZabbixTriggerRuntime trigger : triggers) {
      ZabbixTriggerRuntime updated = resolveTrigger(trigger, templateMacros);
      if (updated != null) {
        resolved.add(updated);
      }
    }
    return List.copyOf(resolved);
  }

  public static ZabbixTriggerRuntime resolveTrigger(ZabbixTriggerRuntime trigger, Map<String, String> templateMacros) {
    if (trigger == null || trigger.expression() == null || trigger.expression().isBlank()) {
      return null;
    }
    String resolvedExpression = applyTemplateMacros(trigger.expression(), templateMacros);
    if (containsUnresolvedTemplateMacroReference(resolvedExpression)) {
      log.warn(
          "Skip trigger '{}' due to unresolved template macros in expression: {}",
          firstNonBlank(trigger.name(), trigger.uuid()),
          resolvedExpression
      );
      return null;
    }
    String resolvedRecovery =
        trigger.recoveryExpression() == null || trigger.recoveryExpression().isBlank()
            ? null
            : applyTemplateMacros(trigger.recoveryExpression(), templateMacros);
    if (containsUnresolvedTemplateMacroReference(resolvedRecovery)) {
      log.warn(
          "Trigger '{}' has unresolved macros in recovery expression, fallback to implicit recovery: {}",
          firstNonBlank(trigger.name(), trigger.uuid()),
          resolvedRecovery
      );
      resolvedRecovery = null;
    }
    return new ZabbixTriggerRuntime(
        trigger.uuid(),
        trigger.name(),
        resolvedExpression,
        trigger.recoveryMode(),
        resolvedRecovery,
        trigger.dependencyKeys(),
        trigger.tags(),
        trigger.manualClose(),
        trigger.priority(),
        trigger.discoveryPrototype(),
        trigger.discoveryRuleKey()
    );
  }

  public static List<ZabbixTriggerRuntime> compileTriggerRecords(
      List<ZabbixTriggerRecord> triggers,
      boolean discoveryPrototype,
      String discoveryRuleKey,
      Map<String, String> templateMacros
  ) {
    if (triggers == null) {
      return List.of();
    }
    List<ZabbixTriggerRuntime> compiled = new ArrayList<>();
    for (ZabbixTriggerRecord trigger : triggers) {
      if (trigger == null || trigger.expression() == null || trigger.expression().isBlank()) {
        continue;
      }
      String resolvedExpression = applyTemplateMacros(trigger.expression(), templateMacros);
      if (containsUnresolvedTemplateMacroReference(resolvedExpression)) {
        log.warn(
            "Skip trigger '{}' due to unresolved template macros in expression: {}",
            firstNonBlank(trigger.name(), trigger.uuid()),
            resolvedExpression
        );
        continue;
      }
      String resolvedRecovery =
          trigger.recoveryExpression() == null || trigger.recoveryExpression().isBlank()
              ? null
              : applyTemplateMacros(trigger.recoveryExpression(), templateMacros);
      if (containsUnresolvedTemplateMacroReference(resolvedRecovery)) {
        log.warn(
            "Trigger '{}' has unresolved macros in recovery expression, fallback to implicit recovery: {}",
            firstNonBlank(trigger.name(), trigger.uuid()),
            resolvedRecovery
        );
        resolvedRecovery = null;
      }
      compiled.add(new ZabbixTriggerRuntime(
          trigger.uuid(),
          firstNonBlank(trigger.name(), trigger.expression()),
          resolvedExpression,
          firstNonBlank(trigger.recoveryMode(), "EXPRESSION"),
          resolvedRecovery,
          trigger.dependencies() == null ? List.of() : trigger.dependencies().stream()
              .map(dep -> dep == null ? null : firstNonBlank(dep.expression(), dep.name()))
              .filter(v -> v != null && !v.isBlank())
              .toList(),
          trigger.tags() == null ? List.of() : List.copyOf(trigger.tags()),
          isEnabledFlag(trigger.manualClose()),
          firstNonBlank(trigger.priority(), "WARNING"),
          discoveryPrototype,
          discoveryRuleKey
      ));
    }
    return List.copyOf(compiled);
  }

  public static ZabbixDiscoveryFilterRecord resolveDiscoveryFilter(
      ZabbixDiscoveryFilterRecord filter,
      Map<String, String> templateMacros
  ) {
    if (filter == null || filter.conditions() == null || filter.conditions().isEmpty()) {
      return null;
    }
    List<ZabbixDiscoveryConditionRecord> compiledConditions = filter.conditions().stream()
        .filter(condition -> condition != null && condition.macro() != null && !condition.macro().isBlank())
        .map(condition -> new ZabbixDiscoveryConditionRecord(
            condition.macro(),
            applyTemplateMacros(condition.value(), templateMacros),
            condition.operator()
        ))
        .toList();
    if (compiledConditions.isEmpty()) {
      return null;
    }
    return new ZabbixDiscoveryFilterRecord(
        filter.evaltype() == null || filter.evaltype().isBlank() ? "AND" : filter.evaltype(),
        List.copyOf(compiledConditions)
    );
  }

  public static Map<String, ZabbixDiscoveryRuleRuntime> resolveDiscoveryRules(
      Map<String, ZabbixDiscoveryRuleRuntime> discoveryRules,
      Map<String, String> templateMacros
  ) {
    if (discoveryRules == null || discoveryRules.isEmpty()) {
      return Map.of();
    }
    Map<String, ZabbixDiscoveryRuleRuntime> resolved = new LinkedHashMap<>();
    for (Map.Entry<String, ZabbixDiscoveryRuleRuntime> entry : discoveryRules.entrySet()) {
      ZabbixDiscoveryRuleRuntime rule = entry.getValue();
      if (rule == null) {
        continue;
      }
      resolved.put(entry.getKey(), new ZabbixDiscoveryRuleRuntime(
          rule.uuid(),
          rule.key(),
          rule.name(),
          rule.type(),
          rule.snmpOid(),
          rule.masterItemKey(),
          rule.preprocessing(),
          rule.lldMacroPaths(),
          rule.delaySeconds(),
          rule.lifetimeSeconds(),
          resolveDiscoveryFilter(rule.filter(), templateMacros),
          rule.itemPrototypes(),
          resolveTriggerList(rule.triggerPrototypes(), templateMacros),
          rule.graphPrototypes()
      ));
    }
    return Map.copyOf(resolved);
  }

  public static Map<String, ZabbixTriggerRuntime> resolveTriggers(
      Map<String, ZabbixTriggerRuntime> triggers,
      Map<String, String> templateMacros
  ) {
    if (triggers == null || triggers.isEmpty()) {
      return Map.of();
    }
    Map<String, ZabbixTriggerRuntime> resolved = new LinkedHashMap<>();
    for (ZabbixTriggerRuntime trigger : resolveTriggerList(triggers.values().stream().toList(), templateMacros)) {
      resolved.put(firstNonBlank(trigger.uuid(), trigger.expression()), trigger);
    }
    return Map.copyOf(resolved);
  }

  private static List<MacroSpan> findTemplateMacroReferences(String value) {
    List<MacroSpan> spans = new ArrayList<>();
    for (int i = 0; i < value.length() - 1; i++) {
      if (value.charAt(i) != '{' || value.charAt(i + 1) != '$') {
        continue;
      }
      int end = findMacroReferenceEnd(value, i);
      if (end < 0) {
        break;
      }
      spans.add(new MacroSpan(i, end + 1, value.substring(i, end + 1)));
      i = end;
    }
    return spans;
  }

  private static int findMacroReferenceEnd(String value, int startIndex) {
    int depth = 1;
    boolean quoted = false;
    for (int i = startIndex + 2; i < value.length(); i++) {
      char current = value.charAt(i);
      if (current == '"' && (i == 0 || value.charAt(i - 1) != '\\')) {
        quoted = !quoted;
      }
      if (quoted) {
        continue;
      }
      if (current == '{') {
        depth++;
      } else if (current == '}') {
        depth--;
        if (depth == 0) {
          return i;
        }
      }
    }
    return -1;
  }

  private static String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static boolean isEnabledFlag(String manualClose) {
    return manualClose != null && !manualClose.isBlank()
        && !"0".equals(manualClose.trim())
        && !"false".equalsIgnoreCase(manualClose.trim());
  }

  private record MacroSpan(int startIndex, int endIndex, String value) {
  }
}
