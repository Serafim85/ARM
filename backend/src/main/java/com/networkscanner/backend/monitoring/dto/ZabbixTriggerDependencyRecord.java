package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixTriggerDependencyRecord(
    String name,
    String expression,
    @JsonProperty("recovery_expression") String recoveryExpression
) {
}
