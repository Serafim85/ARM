package com.networkscanner.backend.workstation.dto;

import java.time.OffsetDateTime;

public record WorkstationLogEntryDto(
    OffsetDateTime recordedAt,
    String level,
    String message,
    String source
) {}
