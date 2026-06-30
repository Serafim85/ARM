package com.networkscanner.backend.integration.event;

import com.networkscanner.backend.integration.api.WislaEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class WislaEventBridge {

  private final WislaEventPublisher publisher;

  public WislaEventBridge(WislaEventPublisher publisher) {
    this.publisher = publisher;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onAvailabilityChanged(WislaAvailabilityChangedEvent event) {
    publisher.publishAvailability(event.payload());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onIncidentChanged(WislaIncidentChangedEvent event) {
    publisher.publishIncident(event.payload());
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
  public void onMonitorStateSnapshot(WislaMonitorStateSnapshotEvent event) {
    publisher.publishMonitorStateSnapshot(event.payload());
  }
}
