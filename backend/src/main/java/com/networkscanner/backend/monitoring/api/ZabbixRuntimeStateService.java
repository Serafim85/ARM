package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ZabbixRuntimeStateService {

  Map<String, List<DiscoveryInstanceRuntime>> loadActiveDiscoveryInstances(MonitoredDeviceEntity device);

  void replaceDiscoveryInstances(
      MonitoredDeviceEntity device,
      String templateId,
      String discoveryRuleKey,
      List<DiscoveryInstanceRuntime> instances
  );

  Map<String, ItemStateSnapshot> loadItemState(MonitoredDeviceEntity device);

  List<ItemStateSnapshot> loadItemStateList(MonitoredDeviceEntity device);

  void saveItemValues(
      MonitoredDeviceEntity device,
      String templateId,
      String templateVersion,
      String packVersion,
      List<ZabbixItemValue> values,
      OffsetDateTime timestamp
  );

  List<Double> loadRecentNumericValues(
      MonitoredDeviceEntity device,
      String metricName,
      String instanceKey,
      OffsetDateTime since,
      Integer limit
  );

  Map<MetricHistoryRequest, List<MetricHistoryPoint>> loadMetricHistoryBatch(
      MonitoredDeviceEntity device,
      List<MetricHistoryRequest> requests
  );

  void removeItemState(Long deviceId, String itemUuid, String instanceKey);

  /** Removes time-series history for devices taken off monitoring (keyed by {@code device_ip}). */
  void purgeDeviceHistory(Collection<String> deviceIps);
}
