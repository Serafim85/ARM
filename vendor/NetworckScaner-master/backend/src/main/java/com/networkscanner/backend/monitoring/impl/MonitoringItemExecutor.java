package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

interface MonitoringItemExecutor {

  boolean supports(MaterializedZabbixItem item);

  List<ZabbixItemValue> execute(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      List<MaterializedZabbixItem> items,
      Map<String, ItemStateSnapshot> state,
      Map<String, ZabbixItemValue> currentCycleValues,
      OffsetDateTime timestamp
  );
}
