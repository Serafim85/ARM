package com.networkscanner.backend.monitoring.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class MonitoringTemplateSelectionSupport {

  private MonitoringTemplateSelectionSupport() {
  }

  static List<String> normalize(List<String> templateIds) {
    if (templateIds == null || templateIds.isEmpty()) {
      return List.of();
    }
    Set<String> ordered = new LinkedHashSet<>();
    for (String candidate : templateIds) {
      if (candidate == null) {
        continue;
      }
      String trimmed = candidate.trim();
      if (!trimmed.isEmpty()) {
        ordered.add(trimmed);
      }
    }
    return List.copyOf(ordered);
  }

  static List<String> parseStored(String storedValue, String fallbackTemplateId) {
    List<String> parsed = parseCsv(storedValue);
    if (!parsed.isEmpty()) {
      return parsed;
    }
    if (fallbackTemplateId == null || fallbackTemplateId.isBlank()) {
      return List.of();
    }
    return List.of(fallbackTemplateId.trim());
  }

  static String toStored(List<String> templateIds) {
    List<String> normalized = normalize(templateIds);
    if (normalized.isEmpty()) {
      return null;
    }
    return String.join(",", normalized);
  }

  private static List<String> parseCsv(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String[] tokens = raw.split(",");
    List<String> values = new ArrayList<>(tokens.length);
    Set<String> dedup = new LinkedHashSet<>();
    for (String token : tokens) {
      if (token == null) {
        continue;
      }
      String trimmed = token.trim();
      if (!trimmed.isEmpty() && dedup.add(trimmed)) {
        values.add(trimmed);
      }
    }
    return List.copyOf(values);
  }
}
