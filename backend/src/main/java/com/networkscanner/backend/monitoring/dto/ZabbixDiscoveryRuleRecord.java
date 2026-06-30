package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixDiscoveryRuleRecord(
    String uuid,
    String name,
    String type,
    @JsonProperty("snmp_oid") String snmpOid,
    String key,
    String delay,
    String lifetime,
    String description,
    ZabbixDiscoveryFilterRecord filter,
    @JsonProperty("master_item") ZabbixMasterItemRef masterItem,
    List<ZabbixPreprocessingStep> preprocessing,
    @JsonProperty("lld_macro_paths") List<ZabbixLldMacroPathRecord> lldMacroPaths,
    @JsonProperty("item_prototypes") List<ZabbixItemRecord> itemPrototypes,
    @JsonProperty("trigger_prototypes") List<ZabbixTriggerRecord> triggerPrototypes,
    @JsonProperty("graph_prototypes") List<ZabbixGraphRecord> graphPrototypes
) {
}
