package com.networkscanner.backend.monitoring.dto;

import java.util.Map;

public record ZabbixValueMapRuntime(
    String uuid,
    String name,
    Map<String, String> mappings
) {
}
