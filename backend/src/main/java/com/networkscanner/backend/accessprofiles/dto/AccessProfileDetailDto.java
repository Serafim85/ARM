package com.networkscanner.backend.accessprofiles.dto;

import java.time.OffsetDateTime;

public record AccessProfileDetailDto(
    Long id,
    String name,
    String description,
    boolean snmpV1Enabled,
    Integer snmpV1Port,
    String snmpV1Community,
    boolean hasSnmpV1Community,
    boolean snmpV2Enabled,
    Integer snmpV2Port,
    String snmpV2Community,
    boolean hasSnmpV2Community,
    boolean snmpV3Enabled,
    Integer snmpV3Port,
    String snmpV3SecurityUsername,
    String snmpV3AuthProtocol,
    boolean hasSnmpV3AuthPassword,
    String snmpV3PrivacyProtocol,
    boolean hasSnmpV3PrivacyPassword,
    boolean sshEnabled,
    Integer sshPort,
    String sshUsername,
    boolean hasSshPassword,
    boolean hasSshPrivateKey,
    boolean hasSshPassphrase,
    boolean httpsEnabled,
    Integer httpsPort,
    String httpsUsername,
    boolean hasHttpsPassword,
    boolean hasHttpsClientCert,
    boolean hasHttpsClientKey,
    boolean httpsInsecureSkipVerify,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
