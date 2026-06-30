package com.networkscanner.backend.monitoring.dto;

import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Набор опциональных фильтров для выборки пороговых событий мониторинга.
 *
 * @param status             OPEN / RESOLVED или {@code null} — без фильтра по статусу
 * @param deviceId           ID записи {@code monitored_devices} или {@code null}
 * @param deviceIds          список ID устройств (OR внутри списка); {@code null} или пусто — без фильтра
 * @param deviceTags         теги устройств CSV (OR внутри списка), по {@code monitored_devices.tags_json}; {@code null} или пусто — без фильтра
 * @param breachStartedFrom  нижняя граница {@code breach_started_at} (включительно)
 * @param breachStartedTo    верхняя граница {@code breach_started_at} (включительно)
 * @param normalizedFrom     нижняя граница {@code normalized_at}; учитываются только события с непустой датой нормализации
 * @param normalizedTo       верхняя граница {@code normalized_at}; то же
 * @param minDurationSeconds минимальная длительность инцидента в секундах (от {@code breach_started_at}
 *                           до {@code normalized_at}, для OPEN — до текущего момента на сервере БД)
 * @param maxDurationSeconds максимальная длительность в секундах (та же семантика)
 * @param thresholdLevel     уровень порога (severity) или {@code null} — без фильтра по уровню
 * @param metricNameContains подстрока для поиска по {@code metric_name} (без учёта регистра), {@code null} или пустая — без фильтра
 * @param macAddressContains подстрока для поиска по {@code mac_address} устройства (без учёта регистра), {@code null} или пустая — без фильтра
 * @param deviceIpContains   подстрока для поиска по {@code ip} устройства (без учёта регистра), {@code null} или пустая — без фильтра
 * @param deviceNameContains подстрока для поиска только по полю {@code name} устройства в {@code monitored_devices} (без учёта регистра), {@code null} или пустая — без фильтра
 */
public record MonitoringEventFilter(
    MonitoringEventStatus status,
    Long deviceId,
    List<Long> deviceIds,
    String deviceTags,
    OffsetDateTime breachStartedFrom,
    OffsetDateTime breachStartedTo,
    OffsetDateTime normalizedFrom,
    OffsetDateTime normalizedTo,
    Long minDurationSeconds,
    Long maxDurationSeconds,
    ThresholdLevel thresholdLevel,
    String metricNameContains,
    String macAddressContains,
    String deviceIpContains,
    String deviceNameContains
) {
}
