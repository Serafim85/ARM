package com.networkscanner.backend.agentingest.api;

import com.networkscanner.backend.agentingest.dto.AgentIngestBatchRequest;
import com.networkscanner.backend.agentingest.dto.AgentIngestResponse;

/** Stable boundary for future extract to worker / Kafka consumer (Variant D). */
public interface AgentIngestPort {

  AgentIngestResponse ingest(AgentIngestBatchRequest batch);
}
