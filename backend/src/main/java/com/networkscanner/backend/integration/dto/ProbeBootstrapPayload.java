package com.networkscanner.backend.integration.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record ProbeBootstrapPayload(
    String sourceSystem,
    Long externalDeviceId,
    String ip,
    String hostName,
    String name,
    String serialNumber,
    String macAddress,
    String vendor,
    String model,
    String firmwareVersion,
    List<String> templateIds,
    String effectiveTemplateId,
    String templateVersion,
    String packVersion,
    String schemaVersion,
    OffsetDateTime updatedAt
) {
}
