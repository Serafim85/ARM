package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixTriggerRecord(
    String uuid,
    String expression,
    @JsonProperty("recovery_mode") String recoveryMode,
    @JsonProperty("recovery_expression") String recoveryExpression,
    List<ZabbixTriggerDependencyRecord> dependencies,
    List<ZabbixTagRecord> tags,
    @JsonProperty("manual_close") String manualClose,
    String name,
    String priority,
    String description
) {
}
