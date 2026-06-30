package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;

public interface ThresholdAndStatusService {

  EvaluatedMonitoringEvent evaluate(PolledMetricsEvent event);
}
