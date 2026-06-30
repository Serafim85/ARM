package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixExportDocument(
    @JsonProperty("zabbix_export") ZabbixExportPayload zabbixExport
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZabbixExportPayload(
      String version,
      String date,
      List<ZabbixTemplateGroup> groups,
      List<ZabbixTemplateRecord> templates
  ) {
  }
}
