package com.networkscanner.backend.network.scanjobs.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ScanJobUpsertRequest(
    @NotBlank String name,
    boolean enabled,
    @NotBlank String cron,
    @NotNull @Valid ScanJobRequest request
) {
}

