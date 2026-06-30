package com.networkscanner.backend.users.util;

import java.util.Set;

public final class TableColumnWidthTableKeys {

  public static final String DEVICES = "devices";
  public static final String EVENTS = "events";
  public static final String TEMPLATES = "templates";
  public static final String AUDIT = "audit";
  public static final String USERS = "users";

  public static final int MIN_WIDTH_PX = 48;
  public static final int MAX_WIDTH_PX = 640;

  private static final Set<String> KNOWN = Set.of(DEVICES, EVENTS, TEMPLATES, AUDIT, USERS);

  private TableColumnWidthTableKeys() {
  }

  public static boolean isKnown(String tableKey) {
    return tableKey != null && KNOWN.contains(tableKey.trim());
  }

  public static String normalize(String tableKey) {
    if (tableKey == null) {
      return null;
    }
    String trimmed = tableKey.trim();
    return isKnown(trimmed) ? trimmed : null;
  }

  public static int clampWidth(int width) {
    if (width < MIN_WIDTH_PX) {
      return MIN_WIDTH_PX;
    }
    if (width > MAX_WIDTH_PX) {
      return MAX_WIDTH_PX;
    }
    return width;
  }
}
