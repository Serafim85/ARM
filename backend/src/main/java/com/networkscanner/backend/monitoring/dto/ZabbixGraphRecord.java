package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixGraphRecord(
    String uuid,
    String name,
    String type,
    String width,
    @JsonProperty("graph_items") List<ZabbixGraphItemRecord> graphItems
) {
}
