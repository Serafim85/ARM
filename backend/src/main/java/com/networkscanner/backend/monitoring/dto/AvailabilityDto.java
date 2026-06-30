package com.networkscanner.backend.monitoring.dto;

public record AvailabilityDto(
    String label,
    boolean active,
    String tone
) {
}
