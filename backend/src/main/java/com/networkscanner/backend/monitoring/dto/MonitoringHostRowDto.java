package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MonitoringHostRowDto(
    Long id,
    String hostName,
    String name,
    String serialNumber,
    String ip,
    String domainName,
    Integer snmpPort,
    String macAddress,
    String vendor,
    String model,
    String firmwareVersion,
    String pollingStatus,
    String status,
    String healthStatus,
    String group,
    List<String> tags,
    List<AvailabilityDto> availability
) {
}
