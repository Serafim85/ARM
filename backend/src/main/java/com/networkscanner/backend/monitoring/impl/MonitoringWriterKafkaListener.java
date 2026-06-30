package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.monitoring.api.MonitoringWriterService;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import java.util.List;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = {"monitoring.kafka.enabled", "monitoring.kafka.writer-enabled"},
    havingValue = "true"
)
public class MonitoringWriterKafkaListener {

  private static final Logger log = LoggerFactory.getLogger(MonitoringWriterKafkaListener.class);

  private final MonitoringWriterService monitoringWriterService;
  private final LongAdder batchCount = new LongAdder();
  private final LongAdder eventCount = new LongAdder();
  private final LongAdder totalDurationNanos = new LongAdder();

  public MonitoringWriterKafkaListener(MonitoringWriterService monitoringWriterService) {
    this.monitoringWriterService = monitoringWriterService;
  }

  @KafkaListener(
      topics = "${monitoring.kafka.topics.evaluated}",
      containerFactory = "evaluatedKafkaListenerContainerFactory"
  )
  public void onEvaluatedMonitoringEvents(List<EvaluatedMonitoringEvent> events) {
    long startedAt = System.nanoTime();
    monitoringWriterService.applyBatch(events);
    long elapsedNanos = System.nanoTime() - startedAt;
    int size = events == null ? 0 : events.size();
    batchCount.increment();
    eventCount.add(size);
    totalDurationNanos.add(elapsedNanos);
    if (elapsedNanos > 2_000_000_000L) {
      log.warn("Writer batch slow: batchSize={}, tookMs={}", size, elapsedNanos / 1_000_000L);
    }
    long processedBatches = batchCount.sum();
    if (processedBatches > 0 && processedBatches % 500 == 0) {
      double avgBatchMs = (totalDurationNanos.sum() / 1_000_000.0d) / processedBatches;
      double avgBatchSize = eventCount.sum() / (double) processedBatches;
      log.info(
          "Writer throughput stats: batches={}, events={}, avgBatchSize={}, avgBatchLatencyMs={}",
          processedBatches,
          eventCount.sum(),
          String.format("%.2f", avgBatchSize),
          String.format("%.2f", avgBatchMs)
      );
    }
  }
}
