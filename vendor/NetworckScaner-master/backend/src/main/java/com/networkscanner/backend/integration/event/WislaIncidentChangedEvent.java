package com.networkscanner.backend.integration.event;

import com.networkscanner.backend.integration.dto.ExternalIncidentUpsert;

public record WislaIncidentChangedEvent(ExternalIncidentUpsert payload) {
}
