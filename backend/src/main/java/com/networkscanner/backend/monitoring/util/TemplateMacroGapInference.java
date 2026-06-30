package com.networkscanner.backend.monitoring.util;

import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTemplateRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRecord;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class TemplateMacroGapInference {

  private static final Pattern MACRO_REFERENCE =
      Pattern.compile("\\{\\$([A-Za-z0-9_.]+)(?::[^}]*)?}");

  private static final List<DonorRule> DONOR_RULES = List.of(
      new DonorRule("generic-snmp-macros", List.of("IF.", "NET.IF.")),
      new DonorRule("vfs-fs-macros", List.of("VFS.FS.")),
      new DonorRule("icmp-ping-macros", List.of("ICMP"))
  );

  private TemplateMacroGapInference() {
  }

  public static List<String> inferDonorIds(
      ZabbixTemplateRecord template,
      Map<String, String> catalog
  ) {
    if (template == null) {
      return List.of();
    }
    Set<String> missingBases = new LinkedHashSet<>();
    collectMissingBases(template, catalog, missingBases);
    if (missingBases.isEmpty()) {
      return List.of();
    }
    List<String> donors = new ArrayList<>();
    for (DonorRule rule : DONOR_RULES) {
      if (rule.matchesAny(missingBases)) {
        donors.add(rule.donorId());
      }
    }
    return List.copyOf(donors);
  }

  private static void collectMissingBases(
      ZabbixTemplateRecord template,
      Map<String, String> catalog,
      Set<String> missingBases
  ) {
    collectFromItems(template.items(), catalog, missingBases);
    if (template.discoveryRules() != null) {
      for (ZabbixDiscoveryRuleRecord rule : template.discoveryRules()) {
        if (rule == null) {
          continue;
        }
        collectFromItems(rule.itemPrototypes(), catalog, missingBases);
        collectFromTriggers(rule.triggerPrototypes(), catalog, missingBases);
        if (rule.filter() != null && rule.filter().conditions() != null) {
          for (var condition : rule.filter().conditions()) {
            if (condition != null) {
              collectFromText(condition.value(), catalog, missingBases);
            }
          }
        }
      }
    }
  }

  private static void collectFromItems(
      List<ZabbixItemRecord> items,
      Map<String, String> catalog,
      Set<String> missingBases
  ) {
    if (items == null) {
      return;
    }
    for (ZabbixItemRecord item : items) {
      if (item == null) {
        continue;
      }
      collectFromTriggers(item.triggers(), catalog, missingBases);
    }
  }

  private static void collectFromTriggers(
      List<ZabbixTriggerRecord> triggers,
      Map<String, String> catalog,
      Set<String> missingBases
  ) {
    if (triggers == null) {
      return;
    }
    for (ZabbixTriggerRecord trigger : triggers) {
      if (trigger == null) {
        continue;
      }
      collectFromText(trigger.expression(), catalog, missingBases);
      collectFromText(trigger.recoveryExpression(), catalog, missingBases);
    }
  }

  private static void collectFromText(String value, Map<String, String> catalog, Set<String> missingBases) {
    if (value == null || value.isBlank()) {
      return;
    }
    if (ZabbixTemplateMacroSupport.containsUnresolvedTemplateMacroReference(
        ZabbixTemplateMacroSupport.applyTemplateMacros(value, catalog)
    )) {
      Matcher matcher = MACRO_REFERENCE.matcher(value);
      while (matcher.find()) {
        String base = "{$" + matcher.group(1) + "}";
        if (ZabbixTemplateMacroSupport.resolveTemplateMacroValue(base, catalog) == null) {
          missingBases.add(base);
        }
      }
    }
  }

  private record DonorRule(String donorId, List<String> prefixes) {
    boolean matchesAny(Set<String> missingBases) {
      for (String base : missingBases) {
        String body = base.length() > 3 ? base.substring(2, base.length() - 1) : "";
        for (String prefix : prefixes) {
          if (body.startsWith(prefix)) {
            return true;
          }
        }
      }
      return false;
    }
  }
}
