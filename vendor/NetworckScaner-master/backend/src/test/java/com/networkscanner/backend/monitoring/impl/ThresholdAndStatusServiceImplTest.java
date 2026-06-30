package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.config.MonitoringKafkaProperties;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.MetricHistoryPoint;
import com.networkscanner.backend.monitoring.dto.MetricHistoryRequest;
import com.networkscanner.backend.monitoring.dto.PolledMetricsEvent;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.UploadedMonitoringTemplateEntity;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import com.networkscanner.backend.monitoring.repository.UploadedMonitoringTemplateRepository;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ThresholdAndStatusServiceImplTest {

  private MonitoringTemplateResolver resolver;
  private ZabbixRuntimeStateService stateService;
  private MonitoringEventRepository eventRepository;
  private MonitoringPipelineMessageStore messageStore;
  private UploadedMonitoringTemplateRepository uploadedRepository;

  @BeforeEach
  void setUp() {
    uploadedRepository = mock(UploadedMonitoringTemplateRepository.class);
    AppUserRepository appUserRepository = mock(AppUserRepository.class);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.<UploadedMonitoringTemplateEntity>of());
    MonitoringTemplateObfuscator obfuscator = new MonitoringTemplateObfuscator();
    resolver = new MonitoringTemplateResolverImpl(
        new ObjectMapper(),
        uploadedRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        appUserRepository,
        new MonitoringTemplateArchiveReader(obfuscator),
        obfuscator
    );
    resolver.initialize();

    stateService = mock(ZabbixRuntimeStateService.class);
    eventRepository = mock(MonitoringEventRepository.class);
    messageStore = mock(MonitoringPipelineMessageStore.class);
    when(eventRepository.findByDevice_IdAndStatus(any(), any())).thenReturn(List.of());
    when(messageStore.markProcessed(any(), eq("EVALUATOR"))).thenReturn(true);
  }

  @Test
  void coldStartDoesNotLoadMonitoringItemState() {
    ResolvedMonitoringTemplate template = resolver.resolveTemplateById("network-generic-device-by-snmp");
    Map<MetricHistoryRequest, List<MetricHistoryPoint>> history = new HashMap<>();
    for (MetricHistoryRequest request : TriggerEvaluationSupport.collectHistoryRequests(
        template,
        Map.of(),
        OffsetDateTime.parse("2026-04-03T12:00:00Z")
    )) {
      history.put(request, List.of(new MetricHistoryPoint(OffsetDateTime.now(), 90.0)));
    }
    when(stateService.loadMetricHistoryBatch(any(), any())).thenReturn(history);

    MonitoringKafkaProperties properties = new MonitoringKafkaProperties();
    ThresholdAndStatusServiceImpl service = new ThresholdAndStatusServiceImpl(
        resolver,
        stateService,
        eventRepository,
        messageStore,
        properties
    );

    PolledMetricsEvent event = new PolledMetricsEvent(
        "cold-start-1",
        "1",
        1L,
        "10.10.10.1",
        "Unknown",
        "Generic",
        "network-generic-device-by-snmp",
        "8.0",
        "2026.05.08-vendors",
        OffsetDateTime.now(),
        Map.of(),
        List.of(new ZabbixItemValue(
            "network-generic-device-by-snmp",
            "zabbix[host,snmp,available]",
            "zabbix[host,snmp,available]",
            "",
            null,
            "snmp-availability-uuid",
            0.0,
            "0",
            null,
            null,
            "ok",
            null
        )),
        null,
        null,
        null
    );

    assertNotNull(service.evaluate(event));
    verify(stateService, never()).loadItemState(any());
    verify(stateService).loadMetricHistoryBatch(any(), any());
  }

  @Test
  void opensTemperatureEventOnlyWhenValueExceedsResolvedMacroThreshold() {
    UploadedMonitoringTemplateEntity uploaded = new UploadedMonitoringTemplateEntity();
    uploaded.setTemplateId("uploaded-temp-threshold-template");
    uploaded.setManifestYaml("""
        schemaVersion: "1"
        packVersion: "2026.05.21"
        defaultTemplateId: uploaded-temp-threshold-template
        templates:
          - id: uploaded-temp-threshold-template
            file: uploaded-temp-threshold-template.yaml
            version: "1.0.0"
            type: SNMP
            zabbixTemplate: Uploaded Temp Threshold Template
        """);
    uploaded.setTemplateYaml("""
        {
          "zabbix_export": {
            "version": "8.0",
            "templates": [
              {
                "template": "Uploaded Temp Threshold Template",
                "name": "Uploaded Temp Threshold Template",
                "macros": [
                  {
                    "macro": "{$TEMP_WARN}",
                    "value": "70"
                  }
                ],
                "items": [
                  {
                    "uuid": "temp-item-1",
                    "name": "Temperature",
                    "type": "SNMP_AGENT",
                    "snmp_oid": "1.3.6.1.4.1.9.9.13.1.3.1.3.1",
                    "key": "temp.sensor[1]",
                    "delay": "60",
                    "triggers": [
                      {
                        "uuid": "temp-trigger-1",
                        "name": "Temperature high",
                        "expression": "last(/Uploaded Temp Threshold Template/temp.sensor[1])>{$TEMP_WARN}",
                        "priority": "WARNING"
                      }
                    ]
                  }
                ]
              }
            ]
          }
        }
        """);
    when(uploadedRepository.findAllByOrderByTemplateIdAsc()).thenReturn(List.of(uploaded));
    resolver.initialize();
    when(stateService.loadMetricHistoryBatch(any(), any())).thenReturn(Map.of());

    MonitoringKafkaProperties properties = new MonitoringKafkaProperties();
    ThresholdAndStatusServiceImpl service = new ThresholdAndStatusServiceImpl(
        resolver,
        stateService,
        eventRepository,
        messageStore,
        properties
    );
    OffsetDateTime timestamp = OffsetDateTime.now();

    PolledMetricsEvent belowThreshold = new PolledMetricsEvent(
        "temp-check-1",
        "1",
        5L,
        "10.11.11.105",
        "Cisco",
        "9500",
        "uploaded-temp-threshold-template",
        "8.0",
        "2026.05.21",
        timestamp,
        Map.of(),
        List.of(new ZabbixItemValue(
            "uploaded-temp-threshold-template",
            "temp.sensor[1]",
            "temp.sensor[1]",
            "",
            null,
            "temp-item-1",
            68.0,
            "68",
            null,
            null,
            "ok",
            null
        )),
        null,
        null,
        null
    );

    var firstEvaluation = service.evaluate(belowThreshold);
    assertNotNull(firstEvaluation);
    assertTrue(firstEvaluation.eventMutations().isEmpty());

    PolledMetricsEvent aboveThreshold = new PolledMetricsEvent(
        "temp-check-2",
        "1",
        5L,
        "10.11.11.105",
        "Cisco",
        "9500",
        "uploaded-temp-threshold-template",
        "8.0",
        "2026.05.21",
        timestamp.plusSeconds(30),
        Map.of(),
        List.of(new ZabbixItemValue(
            "uploaded-temp-threshold-template",
            "temp.sensor[1]",
            "temp.sensor[1]",
            "",
            null,
            "temp-item-1",
            75.0,
            "75",
            null,
            null,
            "ok",
            null
        )),
        null,
        null,
        null
    );

    var secondEvaluation = service.evaluate(aboveThreshold);
    assertNotNull(secondEvaluation);
    assertEquals(1, secondEvaluation.eventMutations().size());
    assertEquals(70.0, secondEvaluation.eventMutations().get(0).thresholdValue());
    assertEquals(75.0, secondEvaluation.eventMutations().get(0).actualValue());
    assertFalse(secondEvaluation.eventMutations().get(0).triggerExpression().contains("{$TEMP_WARN}"));
  }
}
