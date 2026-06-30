package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.monitoring.api.MonitoringMetricsPublisher;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import com.networkscanner.backend.monitoring.util.PolledMetricsEventSplitter;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.common.errors.InterruptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "monitoring.kafka.enabled", havingValue = "true")
public class MonitoringMetricsPublisherImpl implements MonitoringMetricsPublisher {

  private static final Logger log = LoggerFactory.getLogger(MonitoringMetricsPublisherImpl.class);

  private final KafkaTemplate<String, Object> kafkaTemplate;
  private final MonitoringKafkaProperties properties;
  private final ObjectMapper objectMapper;

  public MonitoringMetricsPublisherImpl(
      KafkaTemplate<String, Object> kafkaTemplate,
      MonitoringKafkaProperties properties,
      ObjectMapper objectMapper
  ) {
    this.kafkaTemplate = kafkaTemplate;
    this.properties = properties;
    this.objectMapper = objectMapper;
  }

  @Override
  public void publish(PolledMetricsEvent event) {
    long sendTimeoutMs = Math.max(properties.getPublisher().getSendTimeoutMs(), 1L);
    int maxRecordBytes = Math.max(properties.getPublisher().getMaxRecordBytes(), 1);
    List<PolledMetricsEvent> chunks = PolledMetricsEventSplitter.splitForKafka(event, objectMapper, maxRecordBytes);
    if (chunks.size() > 1) {
      log.info(
          "Splitting polled metrics for device {} into {} Kafka records ({} values)",
          event.deviceId(),
          chunks.size(),
          event.values().size()
      );
    }
    try {
      for (PolledMetricsEvent chunk : chunks) {
        sendChunk(chunk, sendTimeoutMs);
      }
    } catch (TimeoutException exception) {
      log.warn(
          "Fast-fail Kafka publish timeout for device {} after {}ms",
          event.deviceId(),
          sendTimeoutMs
      );
      throw new IllegalStateException("Не удалось отправить батч мониторинга в Kafka: timeout.", exception);
    } catch (ExecutionException exception) {
      Throwable cause = exception.getCause() == null ? exception : exception.getCause();
      log.warn(
          "Kafka publish failed for device {}: {}",
          event.deviceId(),
          cause.getClass().getSimpleName() + ": " + cause.getMessage()
      );
      throw new IllegalStateException("Не удалось отправить батч мониторинга в Kafka: broker unavailable.", cause);
    } catch (InterruptException exception) {
      Thread.currentThread().interrupt();
      log.warn("Kafka publish interrupted for device {}", event.deviceId());
      throw new IllegalStateException("Публикация батча мониторинга была прервана.", exception);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Публикация батча мониторинга была прервана.", exception);
    } catch (Exception exception) {
      log.error("Failed to publish monitoring batch for device {}", event.deviceId(), exception);
      throw new IllegalStateException("Не удалось отправить батч мониторинга в Kafka.", exception);
    }
  }

  private void sendChunk(PolledMetricsEvent chunk, long sendTimeoutMs)
      throws InterruptedException, ExecutionException, TimeoutException {
    kafkaTemplate.send(
        properties.getTopics().getPolled(),
        String.valueOf(chunk.deviceId()),
        chunk
    ).get(sendTimeoutMs, TimeUnit.MILLISECONDS);
  }
}
