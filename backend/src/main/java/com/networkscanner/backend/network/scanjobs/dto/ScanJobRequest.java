package com.networkscanner.backend.network.scanjobs.dto;

import com.networkscanner.backend.network.scan.dto.ScanRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ScanJobRequest(
    @NotNull @Valid ScanRequest scan,
    boolean autoMonitoringEnabled,
    List<String> monitoringTemplateIds
) {
  public List<String> normalizedMonitoringTemplateIds() {
    if (monitoringTemplateIds == null || monitoringTemplateIds.isEmpty()) {
      return List.of();
    }
    return monitoringTemplateIds.stream()
        .filter(v -> v != null && !v.isBlank())
        .map(String::trim)
        .distinct()
        .toList();
  }
}

