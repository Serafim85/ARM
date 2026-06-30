package com.networkscanner.backend.workstation.dto;

import java.time.OffsetDateTime;

public record WorkstationListItemDto(
    Long id,
    String hostname,
    String displayName,
    String osType,
    String primaryIp,
    String agentVersion,
    String status,
    OffsetDateTime lastSeenAt
) {
}
