package com.networkscanner.backend.users.dto;

import java.util.List;

public record UserManagementDto(
    Long id,
    String email,
    String displayName,
    boolean enabled,
    String createdAt,
    List<String> roles
) {
}
