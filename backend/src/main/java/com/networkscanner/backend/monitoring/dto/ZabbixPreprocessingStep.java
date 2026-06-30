package com.networkscanner.backend.monitoring.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ZabbixPreprocessingStep(
    String type,
    List<String> parameters,
    @JsonProperty("error_handler") String errorHandler,
    @JsonProperty("error_handler_params") String errorHandlerParams
) {
}
