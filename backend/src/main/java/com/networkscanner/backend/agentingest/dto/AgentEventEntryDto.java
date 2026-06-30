package com.networkscanner.backend.agentingest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import java.time.OffsetDateTime;

public record AgentEventEntryDto(
    @NotBlank String type,
    @NotBlank String message,
    OffsetDateTime clock,
    String severity,
    @JsonProperty("error_code") String errorCode,
    @JsonProperty("error_text") String errorText,
    String source
) {}
