package com.networkscanner.backend.network.scanjobs.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Частичное обновление задачи автосканирования без изменения параметров ScanRequest.
 */
public record ScanJobMetaUpdateRequest(
    @NotBlank String name,
    boolean enabled,
    @NotBlank String cron
) {
}

