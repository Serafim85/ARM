package com.networkscanner.backend.integration.dto;

public record KafkaBrokerMetadataTopics(
    String availability,
    String incidents,
    String monitoringState
) {
}
