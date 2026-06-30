package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixTemplateRecord(
    String uuid,
    String template,
    String name,
    String description,
    List<ZabbixTemplateGroup> groups,
    List<ZabbixItemRecord> items,
    @JsonProperty("discovery_rules") List<ZabbixDiscoveryRuleRecord> discoveryRules,
    List<ZabbixMacroRecord> macros,
    List<ZabbixValueMapRecord> valuemaps,
    List<ZabbixGraphRecord> graphs,
    List<ZabbixTemplateLinkRecord> templates
) {
}
