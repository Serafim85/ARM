package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutation;
import com.networkscanner.backend.monitoring.dto.MonitoringEventMutationAction;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

class MonitoringWriterServiceImplTest {

  @Test
  void appliesEventMutationsInBatches() {
    ZabbixRuntimeStateService stateService = mock(ZabbixRuntimeStateService.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    MonitoringPipelineMessageStore messageStore = mock(MonitoringPipelineMessageStore.class);
    NotificationDispatchService notificationDispatchService = mock(NotificationDispatchService.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    when(messageStore.markProcessed("writer-batch-1", "WRITER")).thenReturn(true);
    when(monitoredDeviceRepository.findById(any())).thenReturn(Optional.empty());

    AtomicInteger updateBatchCalls = new AtomicInteger();
    AtomicInteger resolveBatchCalls = new AtomicInteger();
    AtomicInteger insertBatchCalls = new AtomicInteger();

    doAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("status = 'RESOLVED'")) {
        resolveBatchCalls.incrementAndGet();
        return new int[] {1};
      }
      if (sql.contains("INSERT INTO monitoring_events")) {
        insertBatchCalls.incrementAndGet();
        return new int[] {1};
      }
      if (sql.contains("UPDATE monitoring_events")) {
        updateBatchCalls.incrementAndGet();
        BatchPreparedStatementSetter setter = invocation.getArgument(1);
        return setter.getBatchSize() == 1 ? new int[] {0} : new int[] {1};
      }
      return new int[0];
    }).when(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));

    MonitoringWriterServiceImpl service = new MonitoringWriterServiceImpl(
        stateService,
        jdbcTemplate,
        messageStore,
        notificationDispatchService,
        monitoredDeviceRepository
    );
    EvaluatedMonitoringEvent event = new EvaluatedMonitoringEvent(
        "writer-batch-1",
        "1",
        15L,
        "10.10.10.15",
        "template-1",
        "1.0",
        "pack-1",
        OffsetDateTime.parse("2026-04-03T12:00:00Z"),
        List.of(new ZabbixItemValue("template-1", "cpu", "cpu", "", null, "uuid", 95.0, "95", "%", null, "ok", null)),
        List.of(
            mutation(MonitoringEventMutationAction.OPEN, "trigger-open", "cpu", ThresholdLevel.HIGH),
            mutation(MonitoringEventMutationAction.UPDATE, "trigger-update", "mem", ThresholdLevel.WARNING),
            mutation(MonitoringEventMutationAction.RESOLVE, "trigger-resolve", "disk", ThresholdLevel.WARNING)
        ),
        DeviceHealthStatus.CRITICAL
    );

    service.apply(event);

    verify(stateService).saveItemValues(any(), eq("template-1"), eq("1.0"), eq("pack-1"), eq(event.values()), eq(event.collectedAt()));
    verify(jdbcTemplate).update(anyString(), eq("CRITICAL"), eq(event.collectedAt()), eq(15L));
    assertEquals(2, updateBatchCalls.get());
    assertEquals(1, resolveBatchCalls.get());
    assertEquals(1, insertBatchCalls.get());
  }

  @Test
  void applyBatchUpdatesHealthStatusOncePerDevice() {
    ZabbixRuntimeStateService stateService = mock(ZabbixRuntimeStateService.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    MonitoringPipelineMessageStore messageStore = mock(MonitoringPipelineMessageStore.class);
    NotificationDispatchService notificationDispatchService = mock(NotificationDispatchService.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    when(messageStore.markProcessed(anyString(), eq("WRITER"))).thenReturn(true);
    when(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class))).thenReturn(new int[] {1});
    when(monitoredDeviceRepository.findById(any())).thenReturn(Optional.empty());

    MonitoringWriterServiceImpl service = new MonitoringWriterServiceImpl(
        stateService,
        jdbcTemplate,
        messageStore,
        notificationDispatchService,
        monitoredDeviceRepository
    );
    EvaluatedMonitoringEvent first = new EvaluatedMonitoringEvent(
        "batch-device-1",
        "1",
        15L,
        "10.10.10.15",
        "template-1",
        "1.0",
        "pack-1",
        OffsetDateTime.parse("2026-04-03T12:00:00Z"),
        List.of(),
        List.of(mutation(MonitoringEventMutationAction.UPDATE, "trigger-update-1", "cpu", ThresholdLevel.WARNING)),
        DeviceHealthStatus.WARN
    );
    EvaluatedMonitoringEvent second = new EvaluatedMonitoringEvent(
        "batch-device-2",
        "1",
        15L,
        "10.10.10.15",
        "template-1",
        "1.0",
        "pack-1",
        OffsetDateTime.parse("2026-04-03T12:01:00Z"),
        List.of(),
        List.of(mutation(MonitoringEventMutationAction.RESOLVE, "trigger-update-1", "cpu", ThresholdLevel.WARNING)),
        DeviceHealthStatus.NORM
    );

    service.applyBatch(List.of(first, second));

    verify(jdbcTemplate, times(1)).update(anyString(), eq("NORM"), eq(second.collectedAt()), eq(15L));
  }

  @Test
  void coalescesOpenUpdateResolveIntoSingleInsertWithinBatch() {
    ZabbixRuntimeStateService stateService = mock(ZabbixRuntimeStateService.class);
    JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    MonitoringPipelineMessageStore messageStore = mock(MonitoringPipelineMessageStore.class);
    NotificationDispatchService notificationDispatchService = mock(NotificationDispatchService.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    when(messageStore.markProcessed(anyString(), eq("WRITER"))).thenReturn(true);
    when(monitoredDeviceRepository.findById(any())).thenReturn(Optional.empty());

    AtomicInteger updateBatchCalls = new AtomicInteger();
    AtomicInteger resolveBatchCalls = new AtomicInteger();
    AtomicInteger insertBatchCalls = new AtomicInteger();

    doAnswer(invocation -> {
      String sql = invocation.getArgument(0);
      if (sql.contains("status = 'RESOLVED'")) {
        resolveBatchCalls.incrementAndGet();
        return new int[] {1};
      }
      if (sql.contains("INSERT INTO monitoring_events")) {
        insertBatchCalls.incrementAndGet();
        return new int[] {1};
      }
      if (sql.contains("UPDATE monitoring_events")) {
        updateBatchCalls.incrementAndGet();
        return new int[] {1};
      }
      return new int[0];
    }).when(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));

    MonitoringWriterServiceImpl service = new MonitoringWriterServiceImpl(
        stateService,
        jdbcTemplate,
        messageStore,
        notificationDispatchService,
        monitoredDeviceRepository
    );
    EvaluatedMonitoringEvent opened = new EvaluatedMonitoringEvent(
        "coalesce-open",
        "1",
        25L,
        "10.10.10.25",
        "template-1",
        "1.0",
        "pack-1",
        OffsetDateTime.parse("2026-04-03T12:00:00Z"),
        List.of(),
        List.of(mutation(MonitoringEventMutationAction.OPEN, "trigger-same", "cpu", ThresholdLevel.WARNING)),
        DeviceHealthStatus.WARN
    );
    EvaluatedMonitoringEvent resolved = new EvaluatedMonitoringEvent(
        "coalesce-resolve",
        "1",
        25L,
        "10.10.10.25",
        "template-1",
        "1.0",
        "pack-1",
        OffsetDateTime.parse("2026-04-03T12:01:00Z"),
        List.of(),
        List.of(mutation(MonitoringEventMutationAction.RESOLVE, "trigger-same", "cpu", ThresholdLevel.WARNING)),
        DeviceHealthStatus.NORM
    );

    EvaluatedMonitoringEvent updated = new EvaluatedMonitoringEvent(
        "coalesce-update",
        "1",
        25L,
        "10.10.10.25",
        "template-1",
        "1.0",
        "pack-1",
        OffsetDateTime.parse("2026-04-03T12:00:30Z"),
        List.of(),
        List.of(mutation(MonitoringEventMutationAction.UPDATE, "trigger-same", "cpu", ThresholdLevel.WARNING)),
        DeviceHealthStatus.WARN
    );

    service.applyBatch(List.of(opened, updated, resolved));

    assertEquals(0, updateBatchCalls.get());
    assertEquals(0, resolveBatchCalls.get());
    assertEquals(1, insertBatchCalls.get());
    verify(jdbcTemplate).update(anyString(), eq("NORM"), eq(resolved.collectedAt()), eq(25L));
  }

  private MonitoringEventMutation mutation(
      MonitoringEventMutationAction action,
      String triggerUuid,
      String metricName,
      ThresholdLevel level
  ) {
    return new MonitoringEventMutation(
        action,
        metricName,
        triggerUuid,
        "CPU trigger",
        "last(/Template/cpu)>80",
        "last(/Template/cpu)<70",
        action == MonitoringEventMutationAction.RESOLVE ? "recovery_expression" : null,
        "",
        level,
        80.0,
        95.0,
        OffsetDateTime.parse("2026-04-03T12:00:00Z"),
        OffsetDateTime.parse("2026-04-03T12:05:00Z"),
        "HIGH"
    );
  }
}
