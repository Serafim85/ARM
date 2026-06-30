package com.networkscanner.backend.users.util;

import java.util.Set;

public final class ChartMetricsLayoutIds {

  public static final String SINGLE = "SINGLE";
  public static final String DOUBLE = "DOUBLE";
  public static final String DEFAULT = DOUBLE;

  public static final Set<String> ALL = Set.of(SINGLE, DOUBLE);

  private ChartMetricsLayoutIds() {
  }

  public static boolean isKnown(String value) {
    return value != null && ALL.contains(value.trim().toUpperCase());
  }

  public static String normalizeOrDefault(String value) {
    if (!isKnown(value)) {
      return DEFAULT;
    }
    return value.trim().toUpperCase();
  }
}
