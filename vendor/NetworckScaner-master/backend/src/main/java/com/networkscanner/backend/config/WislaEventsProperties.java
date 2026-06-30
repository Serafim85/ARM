package com.networkscanner.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.integration.wisla-events")
public class WislaEventsProperties {

  private boolean enabled = true;
  private boolean availabilityHeartbeatEnabled = true;
  private long availabilityHeartbeatMs = 300_000L;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isAvailabilityHeartbeatEnabled() {
    return availabilityHeartbeatEnabled;
  }

  public void setAvailabilityHeartbeatEnabled(boolean availabilityHeartbeatEnabled) {
    this.availabilityHeartbeatEnabled = availabilityHeartbeatEnabled;
  }

  public long getAvailabilityHeartbeatMs() {
    return availabilityHeartbeatMs;
  }

  public void setAvailabilityHeartbeatMs(long availabilityHeartbeatMs) {
    this.availabilityHeartbeatMs = availabilityHeartbeatMs;
  }
}
