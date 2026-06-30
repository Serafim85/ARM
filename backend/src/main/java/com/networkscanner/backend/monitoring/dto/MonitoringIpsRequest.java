package com.networkscanner.backend.monitoring.dto;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record MonitoringIpsRequest(
    @NotEmpty List<String> ips
) {
}
