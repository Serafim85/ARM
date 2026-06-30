package com.networkscanner.backend.monitoring.dto;

import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;

/**
 * Набор опциональных фильтров для страницы списка устройств на мониторинге.
 *
 * @param q                общий поиск по имени/хосту/IP/MAC
 * @param ip               подстрока IP-адреса
 * @param macAddress       подстрока MAC-адреса
 * @param pollingStatus    подстрока статуса опроса
 * @param tag              тег устройства (точное совпадение по тегу)
 * @param healthStatus     фильтр по health status
 * @param hostAvailability фильтр по доступности хоста
 */
public record MonitoringHostFilter(
    String q,
    String ip,
    String macAddress,
    String pollingStatus,
    String tag,
    DeviceHealthStatus healthStatus,
    MonitoringHostAvailabilityFilter hostAvailability
) {
}
