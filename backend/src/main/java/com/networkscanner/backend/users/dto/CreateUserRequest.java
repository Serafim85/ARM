package com.networkscanner.backend.users.dto;

import com.networkscanner.backend.users.model.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateUserRequest(
    @Email @NotBlank String email,
    @NotBlank String displayName,
    @Size(min = 6, message = "Пароль должен содержать минимум 6 символов.") String password,
    @NotEmpty List<RoleName> roles,
    boolean enabled
) {
}
