package com.networkscanner.backend.integration.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record KafkaBrokerMetadataResponse(
    String bootstrapServers,
    String schemaVersion,
    KafkaBrokerMetadataTopics topics,
    Map<String, Object> security
) {
}
