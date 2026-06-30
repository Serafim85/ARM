package com.networkscanner.backend.notifications.dto;

import jakarta.validation.constraints.NotBlank;

public record TestNotificationEventRequest(
    @NotBlank String notificationKind,
    @NotBlank String eventCode,
    String deviceIp,
    String deviceName,
    String severity,
    String metricName,
    String deviceTags,
    String details
) {
}
