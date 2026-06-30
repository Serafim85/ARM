package com.networkscanner.backend.notifications.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpsertNotificationSubscriptionRequest(
    Long id,
    @NotNull Boolean enabled,
    @NotBlank String notificationKind,
    @NotBlank String subscriptionType,
    @NotBlank String channel,
    @NotEmpty List<@NotBlank String> eventCodes,
    @NotBlank String recipientEmail,
    String deviceIpFilter,
    String deviceTagFilter,
    String severityFilter,
    String metricFilter,
    String customCondition
) {
}
