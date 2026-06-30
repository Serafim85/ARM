package com.networkscanner.backend.workstation.report;

import java.time.OffsetDateTime;
import java.util.List;

public record WorkstationParkReport(
    OffsetDateTime generatedAt,
    List<WorkstationParkReportRow> registry,
    List<WorkstationRecommendationRow> recommendations
) {
}
