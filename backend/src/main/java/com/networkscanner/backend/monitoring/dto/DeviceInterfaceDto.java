package com.networkscanner.backend.monitoring.dto;

public record DeviceInterfaceDto(
    String name,
    String description,
    String adminStatus,
    String operStatus,
    String lost,
    String nominalSpeed,
    String activeSpeed,
    String purpose,
    String mode,
    String kind
) {
}
