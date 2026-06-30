package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateCoverageReportDto;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixTriggerRuntime;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ThresholdEvaluationServiceImplTest {

  @Test
  void dependencyBlocksChildTriggerEventOpening() {
    MonitoringEventRepository eventRepository = mock(MonitoringEventRepository.class);
    ZabbixRuntimeStateService stateService = mock(ZabbixRuntimeStateService.class);
    when(eventRepository.findByDevice_IdAndStatus(10L, MonitoringEventStatus.OPEN)).thenReturn(List.of());
    when(stateService.loadMetricHistoryBatch(any(), any())).thenAnswer(invocation -> {
      List<MetricHistoryRequest> requests = invocation.getArgument(1);
      return requests.stream().collect(Collectors.toMap(
          request -> request,
          request -> List.of(new MetricHistoryPoint(
              OffsetDateTime.now(),
              "cpu".equals(request.metricName()) ? 90.0 : 95.0
          ))
      ));
    });

    ThresholdEvaluationServiceImpl service = new ThresholdEvaluationServiceImpl(eventRepository, stateService);
    service.evaluateTriggers(
        device(10L),
        templateWithTriggers(
            trigger("parent", "CPU high", "last(/Template/cpu)>80", null, List.of()),
            trigger("child", "Memory high", "last(/Template/mem)>80", null, List.of("parent"))
        ),
        Map.of(),
        Map.of(),
        OffsetDateTime.now()
    );

    ArgumentCaptor<List<MonitoringEventEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventRepository).saveAll(captor.capture());
    List<MonitoringEventEntity> saved = captor.getValue();
    assertEquals(1, saved.size());
    assertEquals("parent", saved.get(0).getTriggerUuid());
  }

  @Test
  void opensSingleEventWhenMultipleTriggersShareMetric() {
    MonitoringEventRepository eventRepository = mock(MonitoringEventRepository.class);
    ZabbixRuntimeStateService stateService = mock(ZabbixRuntimeStateService.class);
    when(eventRepository.findByDevice_IdAndStatus(30L, MonitoringEventStatus.OPEN)).thenReturn(List.of());
    when(stateService.loadMetricHistoryBatch(any(), any())).thenAnswer(invocation -> {
      List<MetricHistoryRequest> requests = invocation.getArgument(1);
      return requests.stream().collect(Collectors.toMap(
          request -> request,
          request -> List.of(new MetricHistoryPoint(OffsetDateTime.now(), 5.0))
      ));
    });

    ThresholdEvaluationServiceImpl service = new ThresholdEvaluationServiceImpl(eventRepository, stateService);
    service.evaluateTriggers(
        device(30L),
        templateWithTriggers(
            trigger("icmp-1", "ICMP response time", "last(/Template/icmppingsec)>1", null, List.of()),
            trigger("icmp-2", "ICMP response time", "last(/Template/icmppingsec)>1", null, List.of()),
            trigger("icmp-3", "ICMP response time", "last(/Template/icmppingsec)>1", null, List.of())
        ),
        Map.of(),
        Map.of(),
        OffsetDateTime.now()
    );

    ArgumentCaptor<List<MonitoringEventEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventRepository).saveAll(captor.capture());
    List<MonitoringEventEntity> saved = captor.getValue();
    assertEquals(1, saved.size());
    assertEquals(MonitoringEventStatus.OPEN, saved.get(0).getStatus());
    assertEquals("icmppingsec", saved.get(0).getMetricName());
  }

  @Test
  void recoveryExpressionControlsEventClosure() {
    MonitoringEventRepository eventRepository = mock(MonitoringEventRepository.class);
    ZabbixRuntimeStateService stateService = mock(ZabbixRuntimeStateService.class);
    MonitoringEventEntity open = new MonitoringEventEntity();
    open.setDevice(device(20L));
    open.setTriggerUuid("cpu-trigger");
    open.setThresholdLevel(ThresholdLevel.HIGH);
    open.setStatus(MonitoringEventStatus.OPEN);
    open.setActualValue(95.0);
    when(eventRepository.findByDevice_IdAndStatus(20L, MonitoringEventStatus.OPEN)).thenReturn(List.of(open));
    when(stateService.loadMetricHistoryBatch(any(), any())).thenAnswer(invocation -> {
      List<MetricHistoryRequest> requests = invocation.getArgument(1);
      return requests.stream().collect(Collectors.toMap(
          request -> request,
          request -> List.of(new MetricHistoryPoint(OffsetDateTime.now(), 20.0))
      ));
    });

    ThresholdEvaluationServiceImpl service = new ThresholdEvaluationServiceImpl(eventRepository, stateService);
    service.evaluateTriggers(
        device(20L),
        templateWithTriggers(
            trigger(
                "cpu-trigger",
                "CPU high",
                "last(/Template/cpu)>80",
                "last(/Template/cpu)<30",
                List.of()
            )
        ),
        Map.of(),
        Map.of(),
        OffsetDateTime.now()
    );

    ArgumentCaptor<List<MonitoringEventEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(eventRepository).saveAll(captor.capture());
    MonitoringEventEntity resolved = captor.getValue().get(0);
    assertEquals(MonitoringEventStatus.RESOLVED, resolved.getStatus());
    assertEquals("recovery_expression", resolved.getRecoveryPath());
  }

  private ResolvedMonitoringTemplate templateWithTriggers(ZabbixTriggerRuntime... triggers) {
    Map<String, ZabbixTriggerRuntime> triggerMap = new LinkedHashMap<>();
    for (ZabbixTriggerRuntime trigger : triggers) {
      triggerMap.put(trigger.uuid(), trigger);
    }
    return new ResolvedMonitoringTemplate(
        "template-id",
        "SNMP",
        "Template",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        triggerMap,
        List.of(),
        Map.of(),
        new MonitoringTemplateCoverageReportDto(List.of(), List.of(), List.of()),
        true
    );
  }

  private ZabbixTriggerRuntime trigger(
      String uuid,
      String name,
      String expression,
      String recoveryExpression,
      List<String> dependencies
  ) {
    return new ZabbixTriggerRuntime(
        uuid,
        name,
        expression,
        recoveryExpression == null ? "EXPRESSION" : "RECOVERY_EXPRESSION",
        recoveryExpression,
        dependencies,
        List.of(),
        false,
        "HIGH",
        false,
        null
    );
  }

  private MonitoredDeviceEntity device(Long id) {
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(id);
    device.setIp("10.10.10." + id);
    return device;
  }
}
