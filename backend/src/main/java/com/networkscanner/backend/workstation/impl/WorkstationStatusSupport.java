package com.networkscanner.backend.workstation.impl;

import com.networkscanner.backend.workstation.model.WorkstationEntity;
import java.time.OffsetDateTime;

final class WorkstationStatusSupport {

  static final String STATUS_ONLINE = "online";
  static final String STATUS_OFFLINE = "offline";

  private WorkstationStatusSupport() {
  }

  static String effectiveStatus(WorkstationEntity entity, int offlineThresholdMinutes, OffsetDateTime now) {
    if (entity == null) {
      return STATUS_OFFLINE;
    }
    OffsetDateTime lastSeenAt = entity.getLastSeenAt();
    if (lastSeenAt == null) {
      return STATUS_OFFLINE;
    }
    if (lastSeenAt.plusMinutes(Math.max(offlineThresholdMinutes, 1)).isBefore(now)) {
      return STATUS_OFFLINE;
    }
    String stored = entity.getStatus();
    if (stored == null || stored.isBlank()) {
      return STATUS_OFFLINE;
    }
    return stored.trim().toLowerCase();
  }
}
