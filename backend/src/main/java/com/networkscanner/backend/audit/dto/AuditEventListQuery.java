package com.networkscanner.backend.audit.dto;

import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import java.time.OffsetDateTime;

/**
 * Фильтр списка событий аудита; все поля опциональны.
 */
public record AuditEventListQuery(
    OffsetDateTime occurredFrom,
    OffsetDateTime occurredTo,
    String actorContains,
    AuditCategory category,
    AuditAction action
) {
}
