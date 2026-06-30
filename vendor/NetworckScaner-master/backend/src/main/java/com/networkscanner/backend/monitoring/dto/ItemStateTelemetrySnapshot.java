package com.networkscanner.backend.monitoring.dto;

/**
 * CPU/RAM/ROM resolved from collected item state (same selection rules as SNMP snapshot telemetry).
 */
public record ItemStateTelemetrySnapshot(
    MonitoringMetricDto cpu,
    Integer ramUsedPercent,
    Integer romUsedPercent
) {
}
