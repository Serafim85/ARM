package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixLldMacroPathRecord(
    @JsonProperty("lld_macro") String lldMacro,
    String path
) {
}
