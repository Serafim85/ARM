package com.networkscanner.backend.monitoring.dto;

public record DemoMonitoringSeedResponseDto(
    boolean alreadySeeded,
    int devicesCreated,
    String message
) {
}
