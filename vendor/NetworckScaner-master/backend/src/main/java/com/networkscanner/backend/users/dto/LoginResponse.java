package com.networkscanner.backend.users.dto;

import java.util.List;

public record LoginResponse(
    String message,
    String accessToken,
    String email,
    String displayName,
    List<String> roles,
    Long userId,
    Long defaultDashboardId,
    Long defaultTopologyId
) {
}
