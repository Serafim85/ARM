package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixDiscoveryConditionRecord(
    String macro,
    String value,
    String operator
) {
}
