package com.networkscanner.backend.monitoring.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    name = {"monitoring.kafka.enabled", "monitoring.kafka.evaluator-enabled"},
    havingValue = "true"
)
public class PolledMetricsBatchAssembler {

  private static final Logger log = LoggerFactory.getLogger(PolledMetricsBatchAssembler.class);

  private final Cache<String, PendingBatch> pending;

  public PolledMetricsBatchAssembler(MonitoringKafkaProperties properties) {
    int expireMinutes = Math.max(1, properties.getCache().getExpireAfterMinutes());
    pending = Caffeine.newBuilder()
        .maximumSize(Math.max(256, properties.getCache().getMaxDevices() * 4L))
        .expireAfterWrite(Duration.ofMinutes(expireMinutes))
        .build();
  }

  public Optional<PolledMetricsEvent> offer(PolledMetricsEvent part) {
    if (!part.isMultiPart()) {
      return Optional.of(part);
    }
    String key = part.deviceId() + ":" + part.pollBatchId();
    PendingBatch batch = pending.get(key, ignored -> new PendingBatch(part.partCount()));
    synchronized (batch) {
      if (batch.partCount != part.partCount()) {
        log.warn(
            "Ignoring polled metrics part with mismatched partCount for device {} batch {}: expected {}, got {}",
            part.deviceId(),
            part.pollBatchId(),
            batch.partCount,
            part.partCount()
        );
        return Optional.empty();
      }
      if (batch.parts.put(part.partIndex(), part) != null) {
        log.warn(
            "Duplicate polled metrics part {} for device {} batch {}",
            part.partIndex(),
            part.deviceId(),
            part.pollBatchId()
        );
        return Optional.empty();
      }
      if (batch.parts.size() < batch.partCount) {
        return Optional.empty();
      }
      pending.invalidate(key);
      return Optional.of(merge(batch));
    }
  }

  private static PolledMetricsEvent merge(PendingBatch batch) {
    List<PolledMetricsEvent> ordered = batch.parts.values().stream()
        .sorted(Comparator.comparingInt(PolledMetricsEvent::partIndex))
        .toList();
    PolledMetricsEvent head = ordered.get(0);
    List<ZabbixItemValue> values = new ArrayList<>();
    for (PolledMetricsEvent part : ordered) {
      values.addAll(part.values());
    }
    return new PolledMetricsEvent(
        head.pollBatchId(),
        head.schemaVersion(),
        head.deviceId(),
        head.deviceIp(),
        head.vendor(),
        head.model(),
        head.templateId(),
        head.templateVersion(),
        head.packVersion(),
        head.collectedAt(),
        head.discoveryInstances(),
        List.copyOf(values),
        null,
        null,
        null
    );
  }

  private static final class PendingBatch {
    private final int partCount;
    private final Map<Integer, PolledMetricsEvent> parts = new HashMap<>();

    private PendingBatch(int partCount) {
      this.partCount = partCount;
    }
  }
}
