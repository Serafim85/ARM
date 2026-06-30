package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record MonitoringDiscoveryInstanceDto(
    String discoveryRuleKey,
    String instanceKey,
    Map<String, String> macros,
    OffsetDateTime lastDiscoveredAt,
    OffsetDateTime expiresAt
) {
}
