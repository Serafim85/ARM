package com.networkscanner.backend.workstation.report;

public record WorkstationRecommendationRow(
    String hostname,
    String status,
    String priority,
    String recommendation
) {
}
