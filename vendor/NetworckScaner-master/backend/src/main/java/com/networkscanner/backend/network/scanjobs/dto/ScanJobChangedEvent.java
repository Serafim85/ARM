package com.networkscanner.backend.network.scanjobs.dto;

/**
 * Событие изменения задачи автосканирования (создание/обновление/enable/disable).
 * Используется для пересоздания расписания без циклических зависимостей бинов.
 */
public record ScanJobChangedEvent(
    long jobId,
    boolean enabled
) {
}

