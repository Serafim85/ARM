package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public interface ThresholdEvaluationService {

  void evaluateTriggers(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      Map<String, ItemStateSnapshot> itemState,
      Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,
      OffsetDateTime timestamp
  );
}
