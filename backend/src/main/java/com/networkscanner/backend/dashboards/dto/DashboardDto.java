package com.networkscanner.backend.dashboards.dto;

import com.networkscanner.backend.dashboards.model.DashboardVisibility;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

public record DashboardDto(
    Long id,
    Long ownerId,
    String name,
    DashboardVisibility visibility,
    Set<Long> sharedUserIds,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt,
    List<WidgetDto> widgets
) {
}
