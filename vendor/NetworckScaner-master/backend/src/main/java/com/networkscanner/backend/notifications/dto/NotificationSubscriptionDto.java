package com.networkscanner.backend.notifications.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record NotificationSubscriptionDto(
    Long id,
    boolean enabled,
    String notificationKind,
    String subscriptionType,
    String channel,
    List<String> eventCodes,
    String recipientEmail,
    String deviceIpFilter,
    String deviceTagFilter,
    String severityFilter,
    String metricFilter,
    String customCondition,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {
}
