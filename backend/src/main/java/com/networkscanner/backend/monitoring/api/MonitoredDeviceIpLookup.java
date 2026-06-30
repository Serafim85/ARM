package com.networkscanner.backend.monitoring.api;

import java.util.Collection;
import java.util.Set;

/** Поиск IP, уже присутствующих среди устройств на мониторинге. */
public interface MonitoredDeviceIpLookup {

  /**
   * Возвращает подмножество {@code ips}, для которых есть запись в {@code monitored_devices}.
   */
  Set<String> findMonitoredIpsIn(Collection<String> ips);
}
