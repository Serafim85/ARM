package com.networkscanner.backend.dashboards.dto;

import com.networkscanner.backend.dashboards.model.DashboardVisibility;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.LinkedHashSet;
import java.util.Set;

public record DashboardCreateRequest(
    @NotBlank(message = "Укажите название дашборда.")
    String name,
    @NotNull
    DashboardVisibility visibility,
    Set<Long> sharedUserIds
) {
  public DashboardCreateRequest {
    sharedUserIds = sharedUserIds == null ? Set.of() : new LinkedHashSet<>(sharedUserIds);
  }
}
