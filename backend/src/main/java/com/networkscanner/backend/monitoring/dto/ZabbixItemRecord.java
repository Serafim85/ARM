package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixItemRecord(
    String uuid,
    String name,
    String type,
    @JsonProperty("snmp_oid") String snmpOid,
    String key,
    String delay,
    String history,
    String trends,
    @JsonProperty("value_type") String valueType,
    String units,
    String params,
    String url,
    @JsonProperty("master_item") ZabbixMasterItemRef masterItem,
    String description,
    List<ZabbixPreprocessingStep> preprocessing,
    ZabbixValueMapRef valuemap,
    List<ZabbixTagRecord> tags,
    List<ZabbixTriggerRecord> triggers
) {
}
