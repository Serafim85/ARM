package com.networkscanner.backend.audit.dto;

import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import java.time.OffsetDateTime;

public record AuditEventDto(
    OffsetDateTime occurredAt,
    String actorLogin,
    AuditCategory category,
    AuditAction action,
    String target,
    String details
) {
}
