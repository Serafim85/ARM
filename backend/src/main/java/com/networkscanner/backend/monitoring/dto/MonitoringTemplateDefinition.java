package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record MonitoringTemplateDefinition(
    String id,
    String type,
    String name,
    String description,
    @JsonProperty("extends") String extendsTemplate,
    Integer priority,
    MonitoringTemplateMatch match,
    MonitoringTemplateSnmp snmp,
    MonitoringTemplateOids oids,
    Map<String, UnitDefinition> units,
    Map<String, PreprocessingFunctionDefinition> preprocessingFunctions,
    Map<String, MetricDefinition> metrics
) {
}
