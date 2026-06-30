package com.networkscanner.backend.users.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateDirectorySettingsRequest(
    @NotNull Boolean enabled,
    @NotBlank String directoryType,
    @NotBlank String protocol,
    @NotBlank String serverHost,
    @NotNull @Min(1) @Max(65535) Integer serverPort,
    @NotBlank String baseDn,
    @NotBlank String authType,
    String bindDn,
    String bindPassword,
    @NotNull Boolean clearBindPassword,
    @NotBlank String userFilter,
    @NotBlank String loginAttribute,
    @NotBlank String emailAttribute,
    @NotBlank String displayNameAttribute,
    @NotNull Boolean allowLocalFallback
) {
}
