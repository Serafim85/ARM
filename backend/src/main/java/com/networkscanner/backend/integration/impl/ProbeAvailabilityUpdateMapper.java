package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.integration.dto.ProbeAvailability;
import com.networkscanner.backend.integration.dto.ProbeAvailabilityUpdate;
import com.networkscanner.backend.monitoring.impl.MonitoringAvailabilityRefreshResult;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProbeAvailabilityUpdateMapper {

  public ProbeAvailabilityUpdate map(MonitoringAvailabilityRefreshResult result, String sourceSystem) {
    return new ProbeAvailabilityUpdate(
        "1.0",
        UUID.randomUUID().toString(),
        sourceSystem,
        result.deviceId(),
        mapAvailability(result.status()),
        result.recordedAt().toInstant()
    );
  }

  ProbeAvailability mapAvailability(String status) {
    if ("Включено".equals(status)) {
      return ProbeAvailability.AVAILABLE;
    }
    if ("Недоступно".equals(status)) {
      return ProbeAvailability.NOT_AVAILABLE;
    }
    return ProbeAvailability.UNDEFINED;
  }
}
