package com.networkscanner.backend.notifications.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UpdateSmtpSettingsRequest(
    @NotNull Boolean enabled,
    @NotBlank String serverHost,
    @NotNull @Min(1) @Max(65535) Integer serverPort,
    @NotNull Boolean auth,
    @NotNull Boolean starttls,
    @NotNull Boolean ssl,
    String username,
    String password,
    @NotNull Boolean clearPassword,
    @Email @NotBlank String fromEmail
) {
}
