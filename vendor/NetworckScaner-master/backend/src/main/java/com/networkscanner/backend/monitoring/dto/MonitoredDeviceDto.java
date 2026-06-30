package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record MonitoredDeviceDto(
    Long id,
    String ip,
    Integer snmpPort,
    String hostName,
    String name,
    String serialNumber,
    String macAddress,
    String vendor,
    String model,
    String firmwareVersion,
    String pollingStatus,
    String status,
    String healthStatus,
    String groupName,
    List<String> tags,
    List<AvailabilityDto> availability,
    String templateId,
    List<String> templateIds,
    String effectiveTemplateId,
    String templateVersion,
    String packVersion,
    String schemaVersion,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
