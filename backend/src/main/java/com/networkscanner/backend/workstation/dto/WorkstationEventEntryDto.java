package com.networkscanner.backend.workstation.dto;

import java.time.OffsetDateTime;

public record WorkstationEventEntryDto(
    Long id,
    OffsetDateTime recordedAt,
    String eventType,
    String severity,
    String message,
    String errorCode,
    String errorText,
    String source
) {}
