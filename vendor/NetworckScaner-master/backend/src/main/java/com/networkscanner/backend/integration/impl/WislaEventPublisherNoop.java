package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.integration.api.WislaEventPublisher;
import com.networkscanner.backend.integration.dto.ExternalIncidentUpsert;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshot;
import com.networkscanner.backend.integration.dto.ProbeAvailabilityUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "app.integration.wisla-events.enabled", havingValue = "false")
public class WislaEventPublisherNoop implements WislaEventPublisher {

  private static final Logger log = LoggerFactory.getLogger(WislaEventPublisherNoop.class);

  @Override
  public void publishAvailability(ProbeAvailabilityUpdate event) {
    log.debug("Wisla events disabled. Skipping availability event: {}", event.eventId());
  }

  @Override
  public void publishIncident(ExternalIncidentUpsert event) {
    log.debug("Wisla events disabled. Skipping incident event: {}", event.eventId());
  }

  @Override
  public void publishMonitorStateSnapshot(MonitorStateSnapshot event) {
    log.debug("Wisla events disabled. Skipping monitor-state snapshot event: {}", event.eventId());
  }
}
