package com.networkscanner.backend.monitoring.dto;

public record MonitoringMetricDto(
    Double current,
    Double average,
    Double peak,
    String currentItemName,
    String averageItemName,
    String peakItemName
) {
}
