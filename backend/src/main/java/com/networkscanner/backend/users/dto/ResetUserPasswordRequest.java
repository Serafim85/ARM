package com.networkscanner.backend.users.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ResetUserPasswordRequest(
    @NotBlank
    @Size(min = 6, message = "Пароль должен содержать минимум 6 символов.")
    String password
) {
}
