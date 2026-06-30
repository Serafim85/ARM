package com.networkscanner.backend.workstation.report;

import java.time.OffsetDateTime;

public record WorkstationParkReportRow(
    Long id,
    String hostname,
    String displayName,
    String osType,
    String primaryIp,
    String agentVersion,
    String status,
    OffsetDateTime lastSeenAt,
    Double cpuUtilPct,
    Double memUsedBytes,
    Double diskRootUsedPct
) {
}
