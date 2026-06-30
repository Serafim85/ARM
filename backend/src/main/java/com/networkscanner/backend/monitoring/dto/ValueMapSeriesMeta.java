package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

/**
 * Valuemap metadata for a metric series (chart / batch API).
 */
public record ValueMapSeriesMeta(
    String valueMapName,
    Map<String, String> mappings
) {
}
