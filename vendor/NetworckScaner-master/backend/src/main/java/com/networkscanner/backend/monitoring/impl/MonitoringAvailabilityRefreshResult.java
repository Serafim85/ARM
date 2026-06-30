package com.networkscanner.backend.monitoring.impl;

import java.time.OffsetDateTime;
import java.util.Objects;

public record MonitoringAvailabilityRefreshResult(
    Long deviceId,
    String deviceIp,
    String status,
    String previousStatus,
    boolean icmpReachable,
    boolean snmpReachable,
    boolean sshReachable,
    String availabilityJson,
    String previousAvailabilityJson,
    OffsetDateTime previousUpdatedAt,
    OffsetDateTime recordedAt
) {

  boolean stateChanged() {
    return !Objects.equals(status, previousStatus)
        || !Objects.equals(availabilityJson, previousAvailabilityJson);
  }
}
