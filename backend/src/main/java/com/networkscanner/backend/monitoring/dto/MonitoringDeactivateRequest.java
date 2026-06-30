package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MonitoringDeactivateRequest(
    List<Long> deviceIds,
    List<String> ips
) {

  public boolean hasIds() {
    return deviceIds != null && !deviceIds.isEmpty();
  }

  public boolean hasIps() {
    return ips != null && !ips.isEmpty();
  }
}
