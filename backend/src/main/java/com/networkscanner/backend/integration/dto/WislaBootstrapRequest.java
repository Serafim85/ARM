package com.networkscanner.backend.integration.dto;

import java.time.OffsetDateTime;

public record WislaBootstrapRequest(
    int page,
    int size,
    OffsetDateTime updatedSince
) {
}
