package com.networkscanner.backend.integration.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.networkscanner.backend.integration.dto.ProbeAvailability;
import com.networkscanner.backend.monitoring.impl.MonitoringAvailabilityRefreshResult;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ProbeAvailabilityUpdateMapperTest {

  private final ProbeAvailabilityUpdateMapper mapper = new ProbeAvailabilityUpdateMapper();

  @Test
  void mapUsesAvailableForEnabledStatus() {
    MonitoringAvailabilityRefreshResult result = new MonitoringAvailabilityRefreshResult(
        10L,
        "10.0.0.10",
        "Включено",
        "Недоступно",
        true,
        true,
        true,
        "[]",
        "[]",
        OffsetDateTime.now().minusMinutes(5),
        OffsetDateTime.now()
    );

    assertEquals(ProbeAvailability.AVAILABLE, mapper.map(result, "networkscanner").availability());
  }

  @Test
  void mapUsesNotAvailableForDownStatus() {
    MonitoringAvailabilityRefreshResult result = new MonitoringAvailabilityRefreshResult(
        11L,
        "10.0.0.11",
        "Недоступно",
        "Включено",
        false,
        false,
        false,
        "[]",
        "[]",
        OffsetDateTime.now().minusMinutes(5),
        OffsetDateTime.now()
    );

    assertEquals(ProbeAvailability.NOT_AVAILABLE, mapper.map(result, "networkscanner").availability());
  }
}
