package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public record MonitoringTemplateManifestEntry(
    String id,
    String file,
    String version,
    String type,
    MonitoringTemplateSnmp snmp,
    String vendor,
    String modelRegex,
    Integer priority,
    @JsonProperty("extends") String extendsTemplate,
    @JsonProperty("macroDonors") List<String> macroDonors,
    String zabbixTemplate,
    Boolean uiVisible
) {
}
