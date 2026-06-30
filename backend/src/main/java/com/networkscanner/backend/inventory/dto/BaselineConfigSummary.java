package com.networkscanner.backend.inventory.dto;

public record BaselineConfigSummary(
    String fileName,
    String configuredAt,
    String source
) {
}
