package com.networkscanner.backend.users.dto;

public record UserDirectoryEntryDto(
    Long id,
    String email,
    String displayName
) {
}
