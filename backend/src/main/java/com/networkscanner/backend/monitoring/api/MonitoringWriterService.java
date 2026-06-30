package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import java.util.List;

public interface MonitoringWriterService {

  void apply(EvaluatedMonitoringEvent event);

  void applyBatch(List<EvaluatedMonitoringEvent> events);
}
