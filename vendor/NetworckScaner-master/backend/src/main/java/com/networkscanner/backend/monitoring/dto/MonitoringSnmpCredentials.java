package com.networkscanner.backend.monitoring.dto;

/**
 * SNMP-учётные данные при постановке на мониторинг (из текущего сканирования).
 * Не возвращаются в ответах списка устройств.
 */
public record MonitoringSnmpCredentials(
    String snmpVersion,
    String community,
    String securityUsername,
    String authProtocol,
    String authPassword,
    String privacyProtocol,
    String privacyPassword
) {
}
