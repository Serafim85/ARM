package com.networkscanner.backend.users.dto;

public record AdminAuditLogDto(
    String createdAt,
    String actor,
    String action,
    String target,
    String details
) {
}
