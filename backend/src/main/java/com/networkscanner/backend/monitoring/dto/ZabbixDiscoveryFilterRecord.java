package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixDiscoveryFilterRecord(
    String evaltype,
    List<ZabbixDiscoveryConditionRecord> conditions
) {
}
