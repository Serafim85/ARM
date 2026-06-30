package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.api.DependentItemSnapshotPreprocessor;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;

@Component
public class DependentItemSnapshotPreprocessorImpl implements DependentItemSnapshotPreprocessor {

  private final MonitoringPreprocessingEngine preprocessingEngine;

  public DependentItemSnapshotPreprocessorImpl(MonitoringPreprocessingEngine preprocessingEngine) {
    this.preprocessingEngine = preprocessingEngine;
  }

  @Override
  public Double preprocessDependentNumeric(
      ZabbixItemRuntime dependent,
      String masterRawPayload,
      OffsetDateTime now,
      MonitoringPreprocessContext context
  ) {
    if (dependent == null || masterRawPayload == null || masterRawPayload.isBlank()) {
      return null;
    }
    MonitoringPreprocessContext ctx = context == null ? MonitoringPreprocessContext.NONE : context;
    MonitoringPreprocessingEngine.ProcessedMonitoringValue processed =
        preprocessingEngine.process(dependent, masterRawPayload, null, now, ctx);
    if (processed.discarded() || processed.numericValue() == null) {
      return null;
    }
    return processed.numericValue();
  }
}
