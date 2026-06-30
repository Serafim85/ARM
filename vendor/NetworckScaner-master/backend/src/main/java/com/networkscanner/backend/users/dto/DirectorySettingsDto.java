package com.networkscanner.backend.users.dto;

public record DirectorySettingsDto(
    boolean enabled,
    String directoryType,
    String protocol,
    String serverHost,
    Integer serverPort,
    String baseDn,
    String authType,
    String bindDn,
    String bindPassword,
    boolean hasBindPassword,
    String userFilter,
    String loginAttribute,
    String emailAttribute,
    String displayNameAttribute,
    boolean allowLocalFallback
) {
}
