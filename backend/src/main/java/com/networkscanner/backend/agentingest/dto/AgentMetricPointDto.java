package com.networkscanner.backend.agentingest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

public record AgentMetricPointDto(
    @NotBlank String key,
    @NotNull Double value,
    OffsetDateTime clock
) {}
