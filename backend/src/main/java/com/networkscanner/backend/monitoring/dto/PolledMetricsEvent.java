package com.networkscanner.backend.monitoring.dto;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

public record PolledMetricsEvent(
    String messageId,
    String schemaVersion,
    Long deviceId,
    String deviceIp,
    String vendor,
    String model,
    String templateId,
    String templateVersion,
    String packVersion,
    OffsetDateTime collectedAt,
    Map<String, List<DiscoveryInstanceRuntime>> discoveryInstances,
    List<ZabbixItemValue> values,
    String pollBatchId,
    Integer partIndex,
    Integer partCount
) {
  public boolean isMultiPart() {
    return pollBatchId != null && !pollBatchId.isBlank()
        && partCount != null && partCount > 1
        && partIndex != null && partIndex >= 0;
  }
}
