package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;
import java.util.Map;

public record DiscoveryInstanceRuntime(
    String discoveryRuleKey,
    String instanceKey,
    Map<String, String> macros,
    OffsetDateTime lastDiscoveredAt,
    OffsetDateTime expiresAt
) {
}
