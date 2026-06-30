package com.networkscanner.backend.users.util;

import java.util.Set;

public final class ChartLegendPlacementIds {

  public static final String TOP = "TOP";
  public static final String BOTTOM = "BOTTOM";
  public static final String LEFT = "LEFT";
  public static final String RIGHT = "RIGHT";
  public static final String DEFAULT = BOTTOM;

  public static final Set<String> ALL = Set.of(TOP, BOTTOM, LEFT, RIGHT);

  private ChartLegendPlacementIds() {
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
