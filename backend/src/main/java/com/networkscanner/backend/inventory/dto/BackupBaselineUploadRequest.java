package com.networkscanner.backend.inventory.dto;

import jakarta.validation.constraints.NotBlank;

public record BackupBaselineUploadRequest(
    @NotBlank String fileName,
    @NotBlank String content
) {
}
