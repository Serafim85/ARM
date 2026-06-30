package com.networkscanner.backend.monitoring.dto;

import java.util.List;

public record MonitoringTemplateManifest(
    String schemaVersion,
    String packVersion,
    String defaultTemplateId,
    List<MonitoringTemplateManifestEntry> templates
) {
}
