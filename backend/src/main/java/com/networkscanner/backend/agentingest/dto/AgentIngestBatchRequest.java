package com.networkscanner.backend.agentingest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;

public record AgentIngestBatchRequest(
    @NotBlank String hostname,
    @NotNull OffsetDateTime timestamp,
    @JsonProperty("agent_version") String agentVersion,
    @JsonProperty("os_type") String osType,
    @JsonProperty("primary_ip") String primaryIp,
    @Valid List<AgentMetricPointDto> metrics,
    @Valid List<AgentLogEntryDto> logs,
    @Valid List<AgentEventEntryDto> events
) {
  public AgentIngestBatchRequest {
    metrics = metrics == null ? List.of() : List.copyOf(metrics);
    logs = logs == null ? List.of() : List.copyOf(logs);
    events = events == null ? List.of() : List.copyOf(events);
  }
}
