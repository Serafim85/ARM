package com.networkscanner.backend.users.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateUserFromDirectoryRequest(
    @NotBlank String directoryDn,
    @NotBlank String login,
    @NotBlank String email,
    @NotBlank String displayName,
    @NotBlank String role,
    boolean enabled
) {
}
