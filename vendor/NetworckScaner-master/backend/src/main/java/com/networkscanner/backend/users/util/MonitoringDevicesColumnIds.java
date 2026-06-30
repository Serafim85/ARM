package com.networkscanner.backend.users.util;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Идентификаторы колонок таблицы устройств на мониторинге (контракт с frontend). */
public final class MonitoringDevicesColumnIds {

  public static final String HOST_NAME = "hostName";
  public static final String NAME = "name";
  public static final String DEVICE_PARAMS = "deviceParams";
  public static final String SERIES = "series";
  public static final String MODEL = "model";
  public static final String FIRMWARE_VERSION = "firmwareVersion";
  public static final String AVAILABILITY = "availability";
  public static final String PROTOCOL = "protocol";
  public static final String HEALTH_STATUS = "healthStatus";
  public static final String TAGS = "tags";
  public static final String ACTIONS = "actions";

  public static final List<String> ALL = List.of(
      HOST_NAME,
      NAME,
      DEVICE_PARAMS,
      SERIES,
      MODEL,
      FIRMWARE_VERSION,
      AVAILABILITY,
      PROTOCOL,
      HEALTH_STATUS,
      TAGS,
      ACTIONS
  );

  private static final Set<String> KNOWN = Set.copyOf(new LinkedHashSet<>(ALL));

  private MonitoringDevicesColumnIds() {
  }

  public static boolean isKnown(String id) {
    return id != null && KNOWN.contains(id);
  }

  public static int expectedCount() {
    return ALL.size();
  }
}
