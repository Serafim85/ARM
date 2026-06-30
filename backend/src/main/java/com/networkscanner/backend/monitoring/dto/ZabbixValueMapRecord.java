package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixValueMapRecord(
    String uuid,
    String name,
    List<ZabbixValueMapEntry> mappings
) {
}
