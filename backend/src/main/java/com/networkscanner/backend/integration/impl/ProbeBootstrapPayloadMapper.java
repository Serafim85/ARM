package com.networkscanner.backend.integration.impl;

import com.networkscanner.backend.integration.dto.ProbeBootstrapPayload;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ProbeBootstrapPayloadMapper {

  public ProbeBootstrapPayload map(MonitoredDeviceEntity entity, String sourceSystem) {
    return new ProbeBootstrapPayload(
        sourceSystem,
        entity.getId(),
        entity.getIp(),
        entity.getHostName(),
        entity.getName(),
        entity.getSerialNumber(),
        entity.getMacAddress(),
        entity.getVendor(),
        entity.getModel(),
        entity.getFirmwareVersion(),
        parseTemplateIds(entity.getTemplateIds(), entity.getTemplateId()),
        entity.getEffectiveTemplateId(),
        entity.getTemplateVersion(),
        entity.getPackVersion(),
        entity.getSchemaVersion(),
        entity.getUpdatedAt()
    );
  }

  private List<String> parseTemplateIds(String storedValue, String fallbackTemplateId) {
    List<String> parsed = parseCsv(storedValue);
    if (!parsed.isEmpty()) {
      return parsed;
    }
    if (fallbackTemplateId == null || fallbackTemplateId.isBlank()) {
      return List.of();
    }
    return List.of(fallbackTemplateId.trim());
  }

  private List<String> parseCsv(String raw) {
    if (raw == null || raw.isBlank()) {
      return List.of();
    }
    String[] tokens = raw.split(",");
    List<String> values = new ArrayList<>(tokens.length);
    Set<String> dedup = new LinkedHashSet<>();
    for (String token : tokens) {
      if (token == null) {
        continue;
      }
      String trimmed = token.trim();
      if (!trimmed.isEmpty() && dedup.add(trimmed)) {
        values.add(trimmed);
      }
    }
    return List.copyOf(values);
  }
}
