package com.networkscanner.backend.accessprofiles.dto;

public record AccessProfileSummaryDto(
    Long id,
    String name,
    String description,
    boolean snmpV1Enabled,
    boolean snmpV2Enabled,
    boolean snmpV3Enabled,
    boolean sshEnabled,
    boolean httpsEnabled
) {
}
