package com.networkscanner.backend.users.dto;

import jakarta.validation.constraints.NotBlank;

public record DirectoryUserSearchRequest(
    @NotBlank String ldapFilter,
    String emailAttribute,
    String displayNameAttribute
) {
}
