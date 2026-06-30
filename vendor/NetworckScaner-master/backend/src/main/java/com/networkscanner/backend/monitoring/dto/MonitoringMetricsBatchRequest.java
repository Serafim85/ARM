package com.networkscanner.backend.monitoring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.time.OffsetDateTime;
import java.util.List;

public record MonitoringMetricsBatchRequest(
    OffsetDateTime from,
    OffsetDateTime to,
    @Valid @NotEmpty List<MonitoringMetricsBatchSeriesRequest> series,
    /** Максимум точек на ряд для децимации; {@code null}/&le;0 — без децимации. */
    Integer maxPoints
) {
}
