package com.networkscanner.backend.agentingest.dto;

import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public record AgentLogEntryDto(
    @NotBlank String level,
    @NotBlank String message,
    OffsetDateTime clock,
    String source
) {}
