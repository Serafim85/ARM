package com.networkscanner.backend.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record BackupSelectionRequest(
    @NotBlank String backupId
) {
}
