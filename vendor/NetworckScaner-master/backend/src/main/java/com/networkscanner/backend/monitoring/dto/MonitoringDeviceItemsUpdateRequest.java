package com.networkscanner.backend.monitoring.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record MonitoringDeviceItemsUpdateRequest(
    @NotNull List<MonitoringDeviceItemSelectionDto> activeItems
) {
}
