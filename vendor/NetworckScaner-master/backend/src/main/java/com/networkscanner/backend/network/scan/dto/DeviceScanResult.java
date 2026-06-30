package com.networkscanner.backend.network.scan.dto;

import com.networkscanner.backend.monitoring.dto.AvailabilityDto;
import java.util.List;

public record DeviceScanResult(
    String hostName,
    String name,
    String serialNumber,
    String ip,
    String domainName,
    String macAddress,
    String vendor,
    String model,
    String firmwareVersion,
    String pollingStatus,
    String status,
    String group,
    List<String> tags,
    List<AvailabilityDto> availability,
    Integer port,
    Long monitoredDeviceId
) {

  public DeviceScanResult(
      String hostName, String name, String serialNumber, String ip,
      String macAddress, String vendor, String model, String firmwareVersion,
      String pollingStatus, String status, String group, List<AvailabilityDto> availability
  ) {
    this(hostName, name, serialNumber, ip, "-", macAddress, vendor, model,
        firmwareVersion, pollingStatus, status, group, List.of(), availability, null, null);
  }

  public DeviceScanResult withMonitoredDeviceId(Long id) {
    return new DeviceScanResult(hostName, name, serialNumber, ip, domainName, macAddress, vendor, model,
        firmwareVersion, pollingStatus, status, group, tags, availability, port, id);
  }
}
