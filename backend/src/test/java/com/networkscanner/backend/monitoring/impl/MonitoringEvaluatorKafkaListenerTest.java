package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.monitoring.api.ThresholdAndStatusService;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

class MonitoringEvaluatorKafkaListenerTest {

  @Test
  void skipsKafkaPublishWhenNoEvaluatedEvent() {
    ThresholdAndStatusService thresholdService = mock(ThresholdAndStatusService.class);
    KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    MonitoringKafkaProperties properties = new MonitoringKafkaProperties();
    PolledMetricsBatchAssembler batchAssembler = mock(PolledMetricsBatchAssembler.class);
    MonitoringEvaluatorKafkaListener listener = new MonitoringEvaluatorKafkaListener(
        thresholdService,
        batchAssembler,
        kafkaTemplate,
        properties
    );
    PolledMetricsEvent polled = mock(PolledMetricsEvent.class);
    when(batchAssembler.offer(polled)).thenReturn(java.util.Optional.of(polled));
    when(thresholdService.evaluate(polled)).thenReturn(null);

    listener.onPolledMetrics(polled);

    verify(kafkaTemplate, never()).send(any(String.class), any(String.class), any());
  }

  @Test
  void throwsWhenEvaluatedPublishTimesOut() {
    ThresholdAndStatusService thresholdService = mock(ThresholdAndStatusService.class);
    KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    MonitoringKafkaProperties properties = new MonitoringKafkaProperties();
    properties.getPublisher().setSendTimeoutMs(1L);
    properties.getTopics().setEvaluated("monitoring.evaluated");
    PolledMetricsBatchAssembler batchAssembler = mock(PolledMetricsBatchAssembler.class);
    MonitoringEvaluatorKafkaListener listener = new MonitoringEvaluatorKafkaListener(
        thresholdService,
        batchAssembler,
        kafkaTemplate,
        properties
    );
    PolledMetricsEvent polled = mock(PolledMetricsEvent.class);
    EvaluatedMonitoringEvent evaluated = mock(EvaluatedMonitoringEvent.class);
    when(batchAssembler.offer(polled)).thenReturn(java.util.Optional.of(polled));
    when(evaluated.deviceId()).thenReturn(42L);
    when(thresholdService.evaluate(polled)).thenReturn(evaluated);
    when(kafkaTemplate.send(eq("monitoring.evaluated"), eq("42"), eq(evaluated)))
        .thenReturn(new CompletableFuture<>());

    assertThrows(IllegalStateException.class, () -> listener.onPolledMetrics(polled));
  }
}
