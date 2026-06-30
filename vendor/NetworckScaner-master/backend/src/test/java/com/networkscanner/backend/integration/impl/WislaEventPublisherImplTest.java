package com.networkscanner.backend.integration.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.integration.dto.MonitorState;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshot;
import com.networkscanner.backend.integration.dto.MonitorStateSnapshotDevice;
import com.networkscanner.backend.integration.dto.ProbeAvailability;
import com.networkscanner.backend.integration.dto.ProbeAvailabilityUpdate;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class WislaEventPublisherImplTest {

  @Test
  void publishAvailabilitySendsToConfiguredTopic() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    MonitoringKafkaProperties props = new MonitoringKafkaProperties();
    AuditLogService auditLogService = org.mockito.Mockito.mock(AuditLogService.class);
    when(kafkaTemplate.send(any(String.class), any(String.class), any()))
        .thenReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));

    WislaEventPublisherImpl publisher = new WislaEventPublisherImpl(kafkaTemplate, props, auditLogService);
    publisher.publishAvailability(new ProbeAvailabilityUpdate(
        "1.0",
        "evt-1",
        "networkscanner",
        100L,
        ProbeAvailability.AVAILABLE,
        Instant.now()
    ));

    verify(kafkaTemplate).send(eq("wisla.availability"), eq("networkscanner|100"), any());
  }

  @Test
  void publishAvailabilityWritesAuditOnFailure() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    MonitoringKafkaProperties props = new MonitoringKafkaProperties();
    AuditLogService auditLogService = org.mockito.Mockito.mock(AuditLogService.class);
    CompletableFuture<SendResult<String, Object>> failed = new CompletableFuture<>();
    failed.completeExceptionally(new RuntimeException("Kafka down"));
    when(kafkaTemplate.send(any(String.class), any(String.class), any())).thenReturn(failed);

    WislaEventPublisherImpl publisher = new WislaEventPublisherImpl(kafkaTemplate, props, auditLogService);
    publisher.publishAvailability(new ProbeAvailabilityUpdate(
        "1.0",
        "evt-2",
        "networkscanner",
        100L,
        ProbeAvailability.AVAILABLE,
        Instant.now()
    ));

    verify(auditLogService).recordForActor(eq("system"), any(), any(), any(), any());
  }

  @Test
  void publishMonitorStateSnapshotSendsToConfiguredTopic() {
    @SuppressWarnings("unchecked")
    KafkaTemplate<String, Object> kafkaTemplate = org.mockito.Mockito.mock(KafkaTemplate.class);
    MonitoringKafkaProperties props = new MonitoringKafkaProperties();
    AuditLogService auditLogService = org.mockito.Mockito.mock(AuditLogService.class);
    when(kafkaTemplate.send(any(String.class), any(String.class), any()))
        .thenReturn(CompletableFuture.completedFuture((SendResult<String, Object>) null));

    WislaEventPublisherImpl publisher = new WislaEventPublisherImpl(kafkaTemplate, props, auditLogService);
    publisher.publishMonitorStateSnapshot(new MonitorStateSnapshot(
        "1.0",
        "evt-snapshot-1",
        "networkscanner",
        77L,
        new MonitorStateSnapshotDevice(MonitorState.MONITOR_ON, "h", "10.0.0.1", List.of("t:1"), "1")
    ));

    verify(kafkaTemplate).send(eq("wisla.monitor-state"), eq("networkscanner|77"), any());
  }

  @Test
  void probeAvailabilityCheckedAtSerializesAsUtcZ() throws Exception {
    ObjectMapper om = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    String json = om.writeValueAsString(new ProbeAvailabilityUpdate(
        "1.0",
        "e",
        "s",
        1L,
        ProbeAvailability.AVAILABLE,
        Instant.parse("2026-05-07T12:20:09.988689Z")
    ));
    assertTrue(json.contains("2026-05-07T12:20:09.988689Z"));
  }
}
