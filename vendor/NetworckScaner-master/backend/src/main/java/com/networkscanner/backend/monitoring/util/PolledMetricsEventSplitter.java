package com.networkscanner.backend.monitoring.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class PolledMetricsEventSplitter {

  private PolledMetricsEventSplitter() {
  }

  public static List<PolledMetricsEvent> splitForKafka(
      PolledMetricsEvent event,
      ObjectMapper objectMapper,
      int maxSerializedBytes
  ) {
    int limit = Math.max(maxSerializedBytes, 1);
    try {
      if (objectMapper.writeValueAsBytes(event).length <= limit) {
        return List.of(event);
      }
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Не удалось сериализовать батч мониторинга для оценки размера.", exception);
    }

    List<ZabbixItemValue> values = event.values();
    if (values.isEmpty()) {
      return List.of(event);
    }

    String pollBatchId = UUID.randomUUID().toString();
    List<List<ZabbixItemValue>> valueChunks = partitionValues(event, values, objectMapper, limit, pollBatchId);
    List<PolledMetricsEvent> chunks = new ArrayList<>(valueChunks.size());
    int partCount = valueChunks.size();
    for (int partIndex = 0; partIndex < partCount; partIndex++) {
      chunks.add(withValues(event, valueChunks.get(partIndex), pollBatchId, partIndex, partCount));
    }
    return chunks;
  }

  private static List<List<ZabbixItemValue>> partitionValues(
      PolledMetricsEvent event,
      List<ZabbixItemValue> values,
      ObjectMapper objectMapper,
      int maxSerializedBytes,
      String pollBatchId
  ) {
    List<List<ZabbixItemValue>> chunks = new ArrayList<>();
    int start = 0;
    while (start < values.size()) {
      int end = findLargestFittingChunkEnd(event, values, start, objectMapper, maxSerializedBytes, pollBatchId);
      chunks.add(List.copyOf(values.subList(start, end)));
      start = end;
    }
    return chunks;
  }

  private static int findLargestFittingChunkEnd(
      PolledMetricsEvent event,
      List<ZabbixItemValue> values,
      int start,
      ObjectMapper objectMapper,
      int maxSerializedBytes,
      String pollBatchId
  ) {
    int low = start + 1;
    int high = values.size();
    int best = low;
    while (low <= high) {
      int mid = low + (high - low) / 2;
      List<ZabbixItemValue> slice = values.subList(start, mid);
      if (serializedSize(event, slice, objectMapper, pollBatchId, 0, 1) <= maxSerializedBytes) {
        best = mid;
        low = mid + 1;
      } else {
        high = mid - 1;
      }
    }
    if (best == start) {
      best = start + 1;
    }
    return best;
  }

  private static int serializedSize(
      PolledMetricsEvent event,
      List<ZabbixItemValue> slice,
      ObjectMapper objectMapper,
      String pollBatchId,
      int partIndex,
      int partCount
  ) {
    try {
      return objectMapper.writeValueAsBytes(
          withValues(event, slice, pollBatchId, partIndex, partCount)
      ).length;
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Не удалось сериализовать часть батча мониторинга.", exception);
    }
  }

  private static PolledMetricsEvent withValues(
      PolledMetricsEvent event,
      List<ZabbixItemValue> values,
      String pollBatchId,
      int partIndex,
      int partCount
  ) {
    return new PolledMetricsEvent(
        UUID.randomUUID().toString(),
        event.schemaVersion(),
        event.deviceId(),
        event.deviceIp(),
        event.vendor(),
        event.model(),
        event.templateId(),
        event.templateVersion(),
        event.packVersion(),
        event.collectedAt(),
        event.discoveryInstances(),
        values,
        pollBatchId,
        partIndex,
        partCount > 0 ? partCount : null
    );
  }
}
