package com.networkscanner.backend.accessprofiles.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpsertAccessProfileRequest(
    @NotBlank String name,
    String description,
    @NotNull Boolean snmpV1Enabled,
    @Min(1) @Max(65535) Integer snmpV1Port,
    String snmpV1Community,
    Boolean clearSnmpV1Community,
    @NotNull Boolean snmpV2Enabled,
    @Min(1) @Max(65535) Integer snmpV2Port,
    String snmpV2Community,
    Boolean clearSnmpV2Community,
    @NotNull Boolean snmpV3Enabled,
    @Min(1) @Max(65535) Integer snmpV3Port,
    String snmpV3SecurityUsername,
    String snmpV3AuthProtocol,
    String snmpV3AuthPassword,
    Boolean clearSnmpV3AuthPassword,
    String snmpV3PrivacyProtocol,
    String snmpV3PrivacyPassword,
    Boolean clearSnmpV3PrivacyPassword,
    @NotNull Boolean sshEnabled,
    @Min(1) @Max(65535) Integer sshPort,
    String sshUsername,
    String sshPassword,
    Boolean clearSshPassword,
    String sshPrivateKeyPem,
    Boolean clearSshPrivateKey,
    String sshPassphrase,
    Boolean clearSshPassphrase,
    @NotNull Boolean httpsEnabled,
    @Min(1) @Max(65535) Integer httpsPort,
    String httpsUsername,
    String httpsPassword,
    Boolean clearHttpsPassword,
    String httpsClientCertPem,
    Boolean clearHttpsClientCert,
    String httpsClientKeyPem,
    Boolean clearHttpsClientKey,
    @NotNull Boolean httpsInsecureSkipVerify
) {
}
