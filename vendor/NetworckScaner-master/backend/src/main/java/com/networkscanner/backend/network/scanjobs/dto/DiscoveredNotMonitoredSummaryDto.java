package com.networkscanner.backend.network.scanjobs.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Сводка: уникальные IP из последних результатов автосканирования без постановки на мониторинг.")
public record DiscoveredNotMonitoredSummaryDto(
    @Schema(description = "Число уникальных IP")
    long count
) {}
