package com.networkscanner.backend.users.dto;

public record DirectoryRoleMappingDto(
    String groupDn,
    String groupName,
    String role
) {
}
