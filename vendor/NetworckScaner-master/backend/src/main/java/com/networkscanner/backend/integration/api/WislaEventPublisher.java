package com.networkscanner.backend.integration.api;

import com.networkscanner.backend.integration.dto.ExternalIncidentUpsert;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshot;
import com.networkscanner.backend.integration.dto.ProbeAvailabilityUpdate;

public interface WislaEventPublisher {

  void publishAvailability(ProbeAvailabilityUpdate event);

  void publishIncident(ExternalIncidentUpsert event);

  void publishMonitorStateSnapshot(MonitorStateSnapshot event);
}
