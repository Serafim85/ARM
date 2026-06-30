package com.networkscanner.backend.users.util;

import java.util.Set;

public final class ChartMetricsPeriodIds {

  public static final String HOUR = "HOUR";
  public static final String DAY = "DAY";
  public static final String WEEK = "WEEK";
  public static final String MONTH = "MONTH";
  public static final String CUSTOM = "CUSTOM";
  public static final String DEFAULT = DAY;

  public static final Set<String> ALL = Set.of(HOUR, DAY, WEEK, MONTH, CUSTOM);

  private ChartMetricsPeriodIds() {
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
