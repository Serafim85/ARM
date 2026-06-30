package com.networkscanner.backend.agentingest.dto;

public record AgentIngestResponse(
    long workstationId,
    String hostname,
    int metricsAccepted,
    int logsAccepted,
    boolean registered
) {}
