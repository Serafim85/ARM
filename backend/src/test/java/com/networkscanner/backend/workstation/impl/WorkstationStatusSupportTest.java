package com.networkscanner.backend.workstation.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.networkscanner.backend.workstation.model.WorkstationEntity;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class WorkstationStatusSupportTest {

  @Test
  void effectiveStatus_isOfflineWhenLastSeenMissing() {
    WorkstationEntity entity = new WorkstationEntity();
    entity.setStatus("online");
    assertEquals("offline", WorkstationStatusSupport.effectiveStatus(entity, 5, OffsetDateTime.now()));
  }

  @Test
  void effectiveStatus_isOfflineWhenHeartbeatStale() {
    WorkstationEntity entity = new WorkstationEntity();
    entity.setStatus("online");
    entity.setLastSeenAt(OffsetDateTime.now().minusMinutes(10));
    assertEquals("offline", WorkstationStatusSupport.effectiveStatus(entity, 5, OffsetDateTime.now()));
  }

  @Test
  void effectiveStatus_isOnlineWhenHeartbeatRecent() {
    WorkstationEntity entity = new WorkstationEntity();
    entity.setStatus("online");
    entity.setLastSeenAt(OffsetDateTime.now().minusMinutes(1));
    assertEquals("online", WorkstationStatusSupport.effectiveStatus(entity, 5, OffsetDateTime.now()));
  }
}
