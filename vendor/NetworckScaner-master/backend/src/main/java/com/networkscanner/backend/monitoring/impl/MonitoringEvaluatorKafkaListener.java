package com.networkscanner.backend.monitoring.impl;

import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.monitoring.api.ThresholdAndStatusService;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(
    name = {"monitoring.kafka.enabled", "monitoring.kafka.evaluator-enabled"},
    havingValue = "true"
)
public class MonitoringEvaluatorKafkaListener {

  private static final Logger log = LoggerFactory.getLogger(MonitoringEvaluatorKafkaListener.class);

  private final ThresholdAndStatusService thresholdAndStatusService;
  private final PolledMetricsBatchAssembler batchAssembler;
  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final MonitoringKafkaProperties properties;
  private final LongAdder processedCount = new LongAdder();
  private final LongAdder totalDurationNanos = new LongAdder();
  private final LongAdder skippedCount = new LongAdder();

  public MonitoringEvaluatorKafkaListener(
      ThresholdAndStatusService thresholdAndStatusService,
      PolledMetricsBatchAssembler batchAssembler,
      KafkaTemplate<String, Object> kafkaTemplate,
      MonitoringKafkaProperties properties
  ) {
    this.thresholdAndStatusService = thresholdAndStatusService;
    this.batchAssembler = batchAssembler;
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
  }

  @KafkaListener(
      topics = "${monitoring.kafka.topics.polled}",
      containerFactory = "polledKafkaListenerContainerFactory"
  )
  public void onPolledMetrics(PolledMetricsEvent event) {
    long startedAt = System.nanoTime();
    var merged = batchAssembler.offer(event);
    if (merged.isEmpty()) {
      recordEvaluatorLatency(startedAt, false);
      return;
    }
    EvaluatedMonitoringEvent evaluated = thresholdAndStatusService.evaluate(merged.get());
    if (evaluated == null) {
      skippedCount.increment();
      recordEvaluatorLatency(startedAt, false);
      return;
    }
    publishEvaluatedEvent(evaluated);
    recordEvaluatorLatency(startedAt, true);
  }

  private void publishEvaluatedEvent(EvaluatedMonitoringEvent evaluated) {
    long sendTimeoutMs = Math.max(properties.getPublisher().getSendTimeoutMs(), 1L);
    try {
      kafkaTemplate.send(
          properties.getTopics().getEvaluated(),
          String.valueOf(evaluated.deviceId()),
          evaluated
      ).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException exception) {
      log.warn(
          "Evaluator Kafka publish timeout for device {} after {}ms",
          evaluated.deviceId(),
          sendTimeoutMs
      );
      throw new IllegalStateException(
          "Не удалось отправить evaluated event в Kafka: timeout.",
          exception
      );
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause() == null ? exception : exception.getCause();
      log.warn(
          "Evaluator Kafka publish failed for device {}: {}",
          evaluated.deviceId(),
          cause.getClass().getSimpleName() + ": " + cause.getMessage()
      );
      throw new IllegalStateException(
          "Не удалось отправить evaluated event в Kafka: broker unavailable.",
          cause
      );
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      log.warn("Evaluator Kafka publish interrupted for device {}", evaluated.deviceId());
      throw new IllegalStateException("Публикация evaluated event была прервана.", exception);
    }
  }

  private void recordEvaluatorLatency(long startedAt, boolean processed) {
    long elapsedNanos = System.nanoTime() - startedAt;
    totalDurationNanos.add(elapsedNanos);
    if (processed) {
      processedCount.increment();
    }
    long totalProcessed = processedCount.sum();
    if (elapsedNanos > 1_000_000_000L) {
      log.warn("Evaluator processing slow: took {} ms", elapsedNanos / 1_000_000L);
    }
    if (totalProcessed > 0 && totalProcessed % 1000 == 0) {
      double avgMs = (totalDurationNanos.sum() / 1_000_000.0d) / totalProcessed;
      log.info(
          "Evaluator throughput stats: processed={}, skipped={}, avgLatencyMs={}",
          totalProcessed,
          skippedCount.sum(),
          String.format("%.2f", avgMs)
      );
    }
  }
}
