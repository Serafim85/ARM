package com.networkscanner.backend.users.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Идентификаторы колонок таблицы событий мониторинга (контракт с frontend). */
public final class MonitoringEventsColumnIds {

  public static final String BREACH_STARTED_AT = "breachStartedAt";
  public static final String DURATION = "duration";
  public static final String THRESHOLD_LEVEL = "thresholdLevel";
  public static final String STATUS = "status";
  public static final String DEVICE_HOST_NAME = "deviceHostName";
  public static final String DEVICE_NAME = "deviceName";
  public static final String METRIC_NAME = "metricName";
  public static final String THRESHOLD_VALUE = "thresholdValue";
  public static final String ACTUAL_VALUE = "actualValue";

  public static final List<String> ALL = List.of(
      BREACH_STARTED_AT,
      DURATION,
      THRESHOLD_LEVEL,
      STATUS,
      DEVICE_HOST_NAME,
      DEVICE_NAME,
      METRIC_NAME,
      THRESHOLD_VALUE,
      ACTUAL_VALUE
  );

  private static final Set<String> KNOWN = Set.copyOf(new LinkedHashSet<>(ALL));

  private MonitoringEventsColumnIds() {
  }

  public static boolean isKnown(String id) {
    return id != null && KNOWN.contains(id);
  }

  public static int expectedCount() {
    return ALL.size();
  }
}
