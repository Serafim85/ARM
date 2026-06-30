package com.networkscanner.backend.integration.dto;

import java.time.Instant;

public record ProbeAvailabilityUpdate(
    String schemaVersion,
    String eventId,
    String sourceSystem,
    Long externalDeviceId,
    ProbeAvailability availability,
    Instant checkedAt
) {
}
