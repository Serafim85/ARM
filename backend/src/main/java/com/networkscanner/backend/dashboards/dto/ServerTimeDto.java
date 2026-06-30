package com.networkscanner.backend.dashboards.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Текущее время сервера (UTC epoch millis) для синхронизации виджетов.")
public record ServerTimeDto(
    @Schema(description = "Момент времени в миллисекундах с 01.01.1970 UTC", example = "1710000000000")
    long epochMillis
) {
}
