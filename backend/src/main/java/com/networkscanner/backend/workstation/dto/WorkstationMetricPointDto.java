package com.networkscanner.backend.workstation.dto;

import java.time.OffsetDateTime;

public record WorkstationMetricPointDto(
    OffsetDateTime recordedAt,
    double value
) {
}
