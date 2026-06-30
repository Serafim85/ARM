package com.networkscanner.backend.users.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateUserProfileRequest(
    @Email @NotBlank String email,
    @NotBlank String displayName
) {
}
