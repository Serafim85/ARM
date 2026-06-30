package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.networkscanner.backend.integration.event.WislaAvailabilityChangedEvent;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

class MonitoringAvailabilityBatchWriterTest {

  @Test
  void writesHistoryInBatchesAndUpdatesOnlyChangedOrStaleStates() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    AtomicInteger deviceUpdateBatchSize = new AtomicInteger();
    AtomicInteger availabilityHistoryBatchSize = new AtomicInteger();
    AtomicInteger telemetryHistoryBatchSize = new AtomicInteger();

    doAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      BatchPreparedStatementSetter setter = invocation.getArgument(1);
      if (sql.contains("UPDATE monitored_devices")) {
        deviceUpdateBatchSize.set(setter.getBatchSize());
      } else if (sql.contains("INSERT INTO availability_history")) {
        availabilityHistoryBatchSize.set(setter.getBatchSize());
      } else if (sql.contains("INSERT INTO telemetry_history")) {
        telemetryHistoryBatchSize.set(setter.getBatchSize());
      }
      return new int[Math.max(setter.getBatchSize(), 0)];
    }).when(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));

    MonitoringAvailabilityBatchWriter writer = new MonitoringAvailabilityBatchWriter(
        jdbcTemplate,
        300_000L,
        180_000L
    );
    OffsetDateTime recordedAt = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    writer.writeBatch(List.of(
        new MonitoringAvailabilityRefreshResult(
            1L,
            "10.10.10.1",
            "Включено",
            "Недоступно",
            true,
            true,
            true,
            "[{\"label\":\"ICMP\",\"active\":true,\"color\":\"green\"}]",
            "[]",
            recordedAt.minusMinutes(1),
            recordedAt
        ),
        new MonitoringAvailabilityRefreshResult(
            2L,
            "10.10.10.2",
            "Включено",
            "Включено",
            true,
            true,
            true,
            "[{\"label\":\"ICMP\",\"active\":true,\"color\":\"green\"}]",
            "[{\"label\":\"ICMP\",\"active\":true,\"color\":\"green\"}]",
            recordedAt.minusSeconds(30),
            recordedAt
        ),
        new MonitoringAvailabilityRefreshResult(
            3L,
            "10.10.10.3",
            "Недоступно",
            "Недоступно",
            false,
            false,
            false,
            "[{\"label\":\"ICMP\",\"active\":false,\"color\":\"red\"}]",
            "[{\"label\":\"ICMP\",\"active\":false,\"color\":\"red\"}]",
            recordedAt.minusMinutes(10),
            recordedAt
        ),
        new MonitoringAvailabilityRefreshResult(
            4L,
            "10.10.10.4",
            "Недоступно",
            "Недоступно",
            false,
            false,
            false,
            "[{\"label\":\"ICMP\",\"active\":false,\"color\":\"red\"}]",
            "[{\"label\":\"ICMP\",\"active\":false,\"color\":\"red\"}]",
            recordedAt.minusSeconds(20),
            recordedAt
        )
    ));

    assertEquals(2, deviceUpdateBatchSize.get());
    assertEquals(3, availabilityHistoryBatchSize.get());
    assertEquals(3, telemetryHistoryBatchSize.get());
  }

  @Test
  void publishesWislaAvailabilityOnStateChange() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    stubBatchUpdates(jdbcTemplate);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    OffsetDateTime recordedAt = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoringAvailabilityBatchWriter writer = writerWithPublisher(publisher, true, 300_000L);
    writer.writeBatch(List.of(
        changedResult(1L, "10.0.0.1", "Включено", "Недоступно", recordedAt.minusMinutes(1), recordedAt)
    ));

    verify(publisher, times(1)).publishEvent(any(WislaAvailabilityChangedEvent.class));
  }

  @Test
  void publishesWislaAvailabilityHeartbeatWhenIntervalElapsed() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    stubBatchUpdates(jdbcTemplate);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    OffsetDateTime recordedAt = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoringAvailabilityBatchWriter writer = writerWithPublisher(publisher, true, 300_000L);
    writer.writeBatch(List.of(
        stableResult(2L, "10.0.0.2", "Включено", recordedAt.minusMinutes(6), recordedAt)
    ));

    verify(publisher, times(1)).publishEvent(any(WislaAvailabilityChangedEvent.class));
  }

  @Test
  void skipsWislaAvailabilityHeartbeatWhenIntervalNotElapsed() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    stubBatchUpdates(jdbcTemplate);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    OffsetDateTime recordedAt = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoringAvailabilityBatchWriter writer = writerWithPublisher(publisher, true, 300_000L);
    writer.writeBatch(List.of(
        stableResult(2L, "10.0.0.2", "Включено", recordedAt.minusSeconds(30), recordedAt)
    ));

    verify(publisher, never()).publishEvent(any());
  }

  @Test
  void skipsWislaAvailabilityHeartbeatWhenDisabled() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    stubBatchUpdates(jdbcTemplate);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    OffsetDateTime recordedAt = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoringAvailabilityBatchWriter writer = writerWithPublisher(publisher, false, 300_000L);
    writer.writeBatch(List.of(
        stableResult(2L, "10.0.0.2", "Включено", recordedAt.minusMinutes(10), recordedAt)
    ));

    verify(publisher, never()).publishEvent(any());
  }

  @Test
  void heartbeatStillPublishesWhenStateChangedEvenIfHeartbeatDisabled() {
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    stubBatchUpdates(jdbcTemplate);
    ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
    OffsetDateTime recordedAt = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoringAvailabilityBatchWriter writer = writerWithPublisher(publisher, false, 300_000L);
    writer.writeBatch(List.of(
        changedResult(1L, "10.0.0.1", "Включено", "Недоступно", recordedAt.minusMinutes(1), recordedAt)
    ));

    ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
    verify(publisher).publishEvent(captor.capture());
    assertEquals(WislaAvailabilityChangedEvent.class, captor.getValue().getClass());
  }

  private static MonitoringAvailabilityBatchWriter writerWithPublisher(
      ApplicationEventPublisher publisher,
      boolean heartbeatEnabled,
      long heartbeatMs
  ) {
    return new MonitoringAvailabilityBatchWriter(
        mock(JdbcTemplate.class),
        300_000L,
        180_000L,
        () -> "networkscanner",
        new com.networkscanner.backend.integration.impl.ProbeAvailabilityUpdateMapper(),
        publisher,
        heartbeatEnabled,
        heartbeatMs
    );
  }

  private static void stubBatchUpdates(JdbcTemplate jdbcTemplate) {
    doAnswer(invocation -> new int[((BatchPreparedStatementSetter) invocation.getArgument(1)).getBatchSize()])
        .when(jdbcTemplate)
        .batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
  }

  private static MonitoringAvailabilityRefreshResult changedResult(
      long deviceId,
      String ip,
      String status,
      String previousStatus,
      OffsetDateTime previousUpdatedAt,
      OffsetDateTime recordedAt
  ) {
    return new MonitoringAvailabilityRefreshResult(
        deviceId,
        ip,
        status,
        previousStatus,
        true,
        true,
        true,
        "[{\"label\":\"ICMP\",\"active\":true,\"color\":\"green\"}]",
        "[]",
        previousUpdatedAt,
        recordedAt
    );
  }

  private static MonitoringAvailabilityRefreshResult stableResult(
      long deviceId,
      String ip,
      String status,
      OffsetDateTime previousUpdatedAt,
      OffsetDateTime recordedAt
  ) {
    String json = "[{\"label\":\"ICMP\",\"active\":true,\"color\":\"green\"}]";
    return new MonitoringAvailabilityRefreshResult(
        deviceId,
        ip,
        status,
        status,
        true,
        true,
        true,
        json,
        json,
        previousUpdatedAt,
        recordedAt
    );
  }
}
