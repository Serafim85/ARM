package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;

public interface MonitoringMetricsPublisher {

  void publish(PolledMetricsEvent event);
}
