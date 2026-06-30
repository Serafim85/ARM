package com.networkscanner.backend.monitoring.impl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.networkscanner.backend.monitoring.api.MonitoringWriterService;
import com.networkscanner.backend.monitoring.dto.EvaluatedMonitoringEvent;
import java.util.List;
import org.junit.jupiter.api.Test;

class MonitoringWriterKafkaListenerTest {

  @Test
  void delegatesBatchToWriterService() {
    MonitoringWriterService writerService = mock(MonitoringWriterService.class);
    MonitoringWriterKafkaListener listener = new MonitoringWriterKafkaListener(writerService);
    List<EvaluatedMonitoringEvent> events = List.of(mock(EvaluatedMonitoringEvent.class), mock(EvaluatedMonitoringEvent.class));

    listener.onEvaluatedMonitoringEvents(events);

    verify(writerService).applyBatch(events);
  }
}
