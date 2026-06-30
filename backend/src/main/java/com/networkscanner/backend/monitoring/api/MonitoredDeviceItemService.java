package com.networkscanner.backend.monitoring.api;

import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemSelectionDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.util.List;
import java.util.Set;

public interface MonitoredDeviceItemService {

  void seedDefaultsForDevice(MonitoredDeviceEntity device, ResolvedMonitoringTemplate template);

  List<MonitoringDeviceItemDto> listDeviceItems(MonitoredDeviceEntity device, ResolvedMonitoringTemplate template);

  List<MonitoringDeviceItemDto> replaceActiveItems(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      List<MonitoringDeviceItemSelectionDto> activeItems
  );

  void deactivateItem(
      MonitoredDeviceEntity device,
      ResolvedMonitoringTemplate template,
      String itemUuid,
      String instanceKey
  );

  Set<ItemActivationKey> loadActivationKeys(Long deviceId);

  record ItemActivationKey(
      String itemUuid,
      String instanceKey
  ) {
  }
}
