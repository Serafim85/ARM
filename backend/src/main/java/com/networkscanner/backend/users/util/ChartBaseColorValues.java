package com.networkscanner.backend.users.util;

import java.util.Set;
import java.util.regex.Pattern;

public final class ChartBaseColorValues {

  public static final String DEFAULT = "#2563eb";

  private static final Pattern HEX_PATTERN = Pattern.compile("^#[0-9A-Fa-f]{6}$");

  private static final Set<String> PRESETS = Set.of(
      "#2563eb",
      "#f59e0b",
      "#ef4444",
      "#0f766e",
      "#8b5cf6",
      "#ec4899"
  );

  private ChartBaseColorValues() {
  }

  public static boolean isKnown(String value) {
    if (value == null || value.isBlank()) {
      return false;
    }
    String normalized = normalizeHex(value);
    return normalized != null && PRESETS.contains(normalized);
  }

  public static String normalizeOrDefault(String value) {
    String normalized = normalizeHex(value);
    if (normalized != null && PRESETS.contains(normalized)) {
      return normalized;
    }
    return DEFAULT;
  }

  private static String normalizeHex(String value) {
    String trimmed = value.trim().toLowerCase();
    if (!trimmed.startsWith("#")) {
      trimmed = "#" + trimmed;
    }
    if (!HEX_PATTERN.matcher(trimmed).matches()) {
      return null;
    }
    return trimmed;
  }
}
