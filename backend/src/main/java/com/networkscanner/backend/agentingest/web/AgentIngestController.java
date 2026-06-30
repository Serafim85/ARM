package com.networkscanner.backend.agentingest.web;

import com.networkscanner.backend.agentingest.api.AgentIngestPort;
import com.networkscanner.backend.agentingest.dto.AgentIngestBatchRequest;
import com.networkscanner.backend.agentingest.dto.AgentIngestResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/agent")
@Tag(name = "WISLA ARM Agent", description = "Ingest API for workstation agents (HTTPS batch, API-key)")
public class AgentIngestController {

  static final String AGENT_KEY_HEADER = "X-Agent-Key";

  private final AgentIngestPort agentIngestPort;
  private final String ingestApiKey;

  public AgentIngestController(
      AgentIngestPort agentIngestPort,
      @Value("${app.agent.ingest-api-key:}") String ingestApiKey
  ) {
    this.agentIngestPort = agentIngestPort;
    this.ingestApiKey = ingestApiKey;
  }

  @PostMapping("/ingest")
  @Operation(summary = "Accept agent metrics/events batch")
  public AgentIngestResponse ingest(
      @RequestHeader(value = AGENT_KEY_HEADER, required = false) String agentKey,
      @Valid @RequestBody AgentIngestBatchRequest batch
  ) {
    validateApiKey(agentKey);
    return agentIngestPort.ingest(batch);
  }

  private void validateApiKey(String agentKey) {
    if (ingestApiKey == null || ingestApiKey.isBlank()) {
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Agent ingest is not configured");
    }
    if (agentKey == null || !ingestApiKey.equals(agentKey)) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid agent API key");
    }
  }
}
