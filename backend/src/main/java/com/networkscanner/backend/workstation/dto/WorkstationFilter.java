package com.networkscanner.backend.workstation.dto;

public record WorkstationFilter(
    String q,
    String status,
    String osType
) {
}
