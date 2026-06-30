package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixGraphItemRecord(
    String sortorder,
    String drawtype,
    String color,
    String type,
    String yaxisside,
    @JsonProperty("calc_fnc") String calcFunction,
    ZabbixGraphItemTarget item
) {
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record ZabbixGraphItemTarget(
      String host,
      String key
  ) {
  }
}
