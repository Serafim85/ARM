package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.monitoring.api.MonitoredDeviceItemService;
import com.networkscanner.backend.monitoring.api.MonitoringMetricsPublisher;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.api.ThresholdEvaluationService;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MetricCollectorServiceImplTest {

  private static MetricCollectorServiceImpl newCollector(
      MonitoredDeviceRepository deviceRepository,
      MonitoringTemplateResolver templateResolver,
      SnmpScanService snmpScanService,
      ThresholdEvaluationService thresholdEvaluationService,
      ZabbixRuntimeStateService runtimeStateService,
      MonitoringMetricsPublisher metricsPublisher,
      MonitoredDeviceItemService monitoredDeviceItemService,
      IcmpMonitoringItemExecutor icmpExecutor,
      SnmpMonitoringItemExecutor snmpExecutor,
      DerivedMonitoringItemExecutor derivedExecutor,
      boolean kafkaEnabled,
      long perDeviceTimeoutMs,
      int threads,
      boolean preSnmpIcmpEnabled,
      String preSnmpFailPolicy
  ) {
    return new MetricCollectorServiceImpl(
        deviceRepository,
        templateResolver,
        snmpScanService,
        thresholdEvaluationService,
        runtimeStateService,
        metricsPublisher,
        monitoredDeviceItemService,
        icmpExecutor,
        snmpExecutor,
        derivedExecutor,
        kafkaEnabled,
        perDeviceTimeoutMs,
        threads,
        preSnmpIcmpEnabled,
        50,
        2,
        preSnmpFailPolicy,
        50,
        500L
    );
  }

  @Test
  void collectAllPollsOnlyActiveItemsWhenAllowlistInitialized() {
    MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    SnmpScanService snmpScanService = mock(SnmpScanService.class);
    ThresholdEvaluationService thresholdEvaluationService = mock(ThresholdEvaluationService.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoringMetricsPublisher metricsPublisher = mock(MonitoringMetricsPublisher.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);
    IcmpMonitoringItemExecutor icmpExecutor = mock(IcmpMonitoringItemExecutor.class);
    SnmpMonitoringItemExecutor snmpExecutor = mock(SnmpMonitoringItemExecutor.class);
    DerivedMonitoringItemExecutor derivedExecutor = mock(DerivedMonitoringItemExecutor.class);

    MetricCollectorServiceImpl service = newCollector(
        deviceRepository,
        templateResolver,
        snmpScanService,
        thresholdEvaluationService,
        runtimeStateService,
        metricsPublisher,
        monitoredDeviceItemService,
        icmpExecutor,
        snmpExecutor,
        derivedExecutor,
        false,
        5_000L,
        1,
        false,
        "snmp_probe"
    );

    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(17L);
    device.setIp("10.10.10.17");
    device.setVendor("Cisco");
    device.setModel("SG500X-48P");
    device.setTemplateId("cisco-ios-by-snmp");
    device.setTemplateIds("cisco-ios-by-snmp");
    device.setItemAllowlistInitialized(true);

    ZabbixItemRuntime active = new ZabbixItemRuntime(
        "uuid-active",
        "active.key",
        "Active",
        "SNMP_AGENT",
        ".1.3.6.1.2.1.1.1.0",
        30,
        "FLOAT",
        "%",
        "",
        null,
        "",
        "",
        List.of(),
        null,
        false,
        null
    );
    ZabbixItemRuntime inactive = new ZabbixItemRuntime(
        "uuid-inactive",
        "inactive.key",
        "Inactive",
        "SNMP_AGENT",
        ".1.3.6.1.2.1.1.2.0",
        30,
        "FLOAT",
        "%",
        "",
        null,
        "",
        "",
        List.of(),
        null,
        false,
        null
    );

    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "zabbix",
        "Template",
        "",
        null,
        "Cisco",
        ".*",
        100,
        "1",
        "1",
        "1",
        null,
        null,
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(
            "active.key", active,
            "inactive.key", inactive
        ),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );

    when(deviceRepository.findAll()).thenReturn(List.of(device));
    when(templateResolver.resolveForDevice(eq(List.of("cisco-ios-by-snmp")), eq("Cisco"), eq("SG500X-48P"), eq(null)))
        .thenReturn(template);
    when(runtimeStateService.loadActiveDiscoveryInstances(device)).thenReturn(Map.of());
    when(runtimeStateService.loadItemState(device)).thenReturn(Map.of());
    when(monitoredDeviceItemService.loadActivationKeys(17L)).thenReturn(Set.of(
        new MonitoredDeviceItemService.ItemActivationKey("uuid-active", "")
    ));
    when(icmpExecutor.supports(any())).thenReturn(true);
    when(icmpExecutor.execute(any(), any(), any(), any(), any(), any())).thenReturn(List.of());
    when(snmpExecutor.supports(any())).thenReturn(false);
    when(derivedExecutor.supports(any())).thenReturn(false);

    service.collectAll();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem>> itemCaptor =
        ArgumentCaptor.forClass(List.class);
    verify(icmpExecutor).execute(
        eq(device),
        eq(template),
        itemCaptor.capture(),
        any(),
        any(),
        any(OffsetDateTime.class)
    );
    assertEquals(1, itemCaptor.getValue().size());
    assertEquals("active.key", itemCaptor.getValue().get(0).key());
  }

  @Test
  void collectAllTreatsNullRuntimeStateCollectionsAsEmpty() {
    MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    SnmpScanService snmpScanService = mock(SnmpScanService.class);
    ThresholdEvaluationService thresholdEvaluationService = mock(ThresholdEvaluationService.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoringMetricsPublisher metricsPublisher = mock(MonitoringMetricsPublisher.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);
    IcmpMonitoringItemExecutor icmpExecutor = mock(IcmpMonitoringItemExecutor.class);
    SnmpMonitoringItemExecutor snmpExecutor = mock(SnmpMonitoringItemExecutor.class);
    DerivedMonitoringItemExecutor derivedExecutor = mock(DerivedMonitoringItemExecutor.class);

    MetricCollectorServiceImpl service = newCollector(
        deviceRepository,
        templateResolver,
        snmpScanService,
        thresholdEvaluationService,
        runtimeStateService,
        metricsPublisher,
        monitoredDeviceItemService,
        icmpExecutor,
        snmpExecutor,
        derivedExecutor,
        false,
        5_000L,
        1,
        false,
        "snmp_probe"
    );

    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(18L);
    device.setIp("10.10.10.18");
    device.setTemplateId("empty-template");
    device.setTemplateIds("empty-template");

    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "zabbix",
        "Template",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        null,
        null,
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        null,
        null,
        Map.of(),
        null,
        List.of(),
        Map.of(),
        null,
        true
    );

    when(deviceRepository.findAll()).thenReturn(List.of(device));
    when(templateResolver.resolveForDevice(eq(List.of("empty-template")), eq(null), eq(null), eq(null)))
        .thenReturn(template);
    when(runtimeStateService.loadActiveDiscoveryInstances(device)).thenReturn(null);
    when(runtimeStateService.loadItemState(device)).thenReturn(null);
    when(icmpExecutor.supports(any())).thenReturn(false);
    when(snmpExecutor.supports(any())).thenReturn(false);
    when(derivedExecutor.supports(any())).thenReturn(false);

    service.collectAll();

    verify(thresholdEvaluationService).evaluateTriggers(
        eq(device),
        eq(template),
        eq(Map.of()),
        eq(Map.of()),
        any(OffsetDateTime.class)
    );
    verify(runtimeStateService, never()).saveItemValues(any(), any(), any(), any(), any(), any());
  }

  @Test
  void collectAllSkipsNullDiscoveryItemPrototypes() {
    MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    SnmpScanService snmpScanService = mock(SnmpScanService.class);
    ThresholdEvaluationService thresholdEvaluationService = mock(ThresholdEvaluationService.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoringMetricsPublisher metricsPublisher = mock(MonitoringMetricsPublisher.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);
    IcmpMonitoringItemExecutor icmpExecutor = mock(IcmpMonitoringItemExecutor.class);
    SnmpMonitoringItemExecutor snmpExecutor = mock(SnmpMonitoringItemExecutor.class);
    DerivedMonitoringItemExecutor derivedExecutor = mock(DerivedMonitoringItemExecutor.class);

    MetricCollectorServiceImpl service = newCollector(
        deviceRepository,
        templateResolver,
        snmpScanService,
        thresholdEvaluationService,
        runtimeStateService,
        metricsPublisher,
        monitoredDeviceItemService,
        icmpExecutor,
        snmpExecutor,
        derivedExecutor,
        false,
        5_000L,
        1,
        false,
        "snmp_probe"
    );

    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(19L);
    device.setIp("10.10.10.19");
    device.setTemplateId("discovery-template");
    device.setTemplateIds("discovery-template");

    com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime discoveryRule =
        new com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime(
            "d1",
            "if.discovery",
            "If discovery",
            "SNMP_AGENT",
            "discovery[{#SNMPINDEX},1.2.3]",
            null,
            List.of(),
            List.of(),
            60,
            3600,
            null,
            null,
            List.of(),
            List.of()
        );

    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "tpl",
        "zabbix",
        "Template",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        null,
        null,
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of("if.discovery", discoveryRule),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );

    when(deviceRepository.findAll()).thenReturn(List.of(device));
    when(templateResolver.resolveForDevice(eq(List.of("discovery-template")), eq(null), eq(null), eq(null)))
        .thenReturn(template);
    when(runtimeStateService.loadActiveDiscoveryInstances(device)).thenReturn(Map.of(
        "if.discovery",
        List.of(new com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime(
            "if.discovery",
            "1",
            Map.of("{#SNMPINDEX}", "1"),
            OffsetDateTime.parse("2026-05-26T10:00:00Z"),
            OffsetDateTime.parse("2026-05-26T11:00:00Z")
        ))
    ));
    when(runtimeStateService.loadItemState(device)).thenReturn(Map.of());
    when(icmpExecutor.supports(any())).thenReturn(false);
    when(snmpExecutor.supports(any())).thenReturn(false);
    when(derivedExecutor.supports(any())).thenReturn(false);

    service.collectAll();

    verify(thresholdEvaluationService).evaluateTriggers(
        eq(device),
        eq(template),
        eq(Map.of()),
        any(),
        any(OffsetDateTime.class)
    );
  }

  @Test
  void preSnmpGateSkipOnlySkipsSnmpWhenIcmpFails() {
    MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    SnmpScanService snmpScanService = mock(SnmpScanService.class);
    ThresholdEvaluationService thresholdEvaluationService = mock(ThresholdEvaluationService.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoringMetricsPublisher metricsPublisher = mock(MonitoringMetricsPublisher.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);
    IcmpMonitoringItemExecutor icmpExecutor = mock(IcmpMonitoringItemExecutor.class);
    SnmpMonitoringItemExecutor snmpExecutor = mock(SnmpMonitoringItemExecutor.class);
    DerivedMonitoringItemExecutor derivedExecutor = mock(DerivedMonitoringItemExecutor.class);

    MetricCollectorServiceImpl service = newCollector(
        deviceRepository,
        templateResolver,
        snmpScanService,
        thresholdEvaluationService,
        runtimeStateService,
        metricsPublisher,
        monitoredDeviceItemService,
        icmpExecutor,
        snmpExecutor,
        derivedExecutor,
        false,
        5_000L,
        2,
        true,
        "skip_only"
    );

    MonitoredDeviceEntity device = unreachableDevice();
    ResolvedMonitoringTemplate template = templateWithSnmpAvailabilityItem();

    when(deviceRepository.findAll()).thenReturn(List.of(device));
    when(snmpScanService.checkIcmpReachable(eq("192.168.51.43"), anyInt())).thenReturn(false);
    when(templateResolver.resolveForDevice(eq(List.of("tpl")), any(), any(), any())).thenReturn(template);
    when(runtimeStateService.loadActiveDiscoveryInstances(device)).thenReturn(Map.of());
    when(runtimeStateService.loadItemState(device)).thenReturn(Map.of());
    when(icmpExecutor.supports(any())).thenReturn(false);
    when(snmpExecutor.supports(any())).thenReturn(true);
    when(derivedExecutor.supports(any())).thenReturn(false);

    service.collectAll();

    verify(snmpExecutor, never()).executeBatch(any(), any(), any(), any(), any());
    verify(snmpScanService, never()).executeDiscovery(any(), any(), any(), any());
  }

  @Test
  void preSnmpGateSnmpProbeRunsFullSnmpWhenProbeSucceeds() {
    MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    SnmpScanService snmpScanService = mock(SnmpScanService.class);
    ThresholdEvaluationService thresholdEvaluationService = mock(ThresholdEvaluationService.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoringMetricsPublisher metricsPublisher = mock(MonitoringMetricsPublisher.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);
    IcmpMonitoringItemExecutor icmpExecutor = mock(IcmpMonitoringItemExecutor.class);
    SnmpMonitoringItemExecutor snmpExecutor = mock(SnmpMonitoringItemExecutor.class);
    DerivedMonitoringItemExecutor derivedExecutor = mock(DerivedMonitoringItemExecutor.class);

    MetricCollectorServiceImpl service = newCollector(
        deviceRepository,
        templateResolver,
        snmpScanService,
        thresholdEvaluationService,
        runtimeStateService,
        metricsPublisher,
        monitoredDeviceItemService,
        icmpExecutor,
        snmpExecutor,
        derivedExecutor,
        false,
        5_000L,
        2,
        true,
        "snmp_probe"
    );

    MonitoredDeviceEntity device = reachableDevice();
    ResolvedMonitoringTemplate template = templateWithSnmpAgentItem();

    when(deviceRepository.findAll()).thenReturn(List.of(device));
    when(snmpScanService.checkIcmpReachable(eq("192.168.51.42"), anyInt())).thenReturn(false);
    when(snmpScanService.checkSnmpReachable(eq("192.168.51.42"), any(ResolvedMonitoringTemplate.class)))
        .thenReturn(true);
    when(templateResolver.resolveForDevice(eq(List.of("tpl")), any(), any(), any())).thenReturn(template);
    when(runtimeStateService.loadActiveDiscoveryInstances(device)).thenReturn(Map.of());
    when(runtimeStateService.loadItemState(device)).thenReturn(Map.of());
    when(icmpExecutor.supports(any())).thenReturn(false);
    when(snmpExecutor.supports(any())).thenReturn(true);
    when(derivedExecutor.supports(any())).thenReturn(false);
    when(snmpExecutor.executeBatch(any(), any(), any(), any(), any()))
        .thenReturn(new SnmpPollBatch(List.of(), Map.of()));

    service.collectAll();

    verify(snmpExecutor).executeBatch(any(), any(), any(), any(), any());
  }

  @Test
  void preSnmpGateUnreachablePublishesSnmpAvailabilityZero() {
    MonitoredDeviceRepository deviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    SnmpScanService snmpScanService = mock(SnmpScanService.class);
    ThresholdEvaluationService thresholdEvaluationService = mock(ThresholdEvaluationService.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoringMetricsPublisher metricsPublisher = mock(MonitoringMetricsPublisher.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);
    IcmpMonitoringItemExecutor icmpExecutor = mock(IcmpMonitoringItemExecutor.class);
    SnmpMonitoringItemExecutor snmpExecutor = mock(SnmpMonitoringItemExecutor.class);
    DerivedMonitoringItemExecutor derivedExecutor = mock(DerivedMonitoringItemExecutor.class);

    MetricCollectorServiceImpl service = newCollector(
        deviceRepository,
        templateResolver,
        snmpScanService,
        thresholdEvaluationService,
        runtimeStateService,
        metricsPublisher,
        monitoredDeviceItemService,
        icmpExecutor,
        snmpExecutor,
        derivedExecutor,
        true,
        5_000L,
        2,
        true,
        "skip_only"
    );

    MonitoredDeviceEntity device = unreachableDevice();
    ResolvedMonitoringTemplate template = templateWithSnmpAvailabilityItem();

    when(deviceRepository.findAll()).thenReturn(List.of(device));
    when(snmpScanService.checkIcmpReachable(eq("192.168.51.43"), anyInt())).thenReturn(false);
    when(templateResolver.resolveForDevice(eq(List.of("tpl")), any(), any(), any())).thenReturn(template);
    when(runtimeStateService.loadActiveDiscoveryInstances(device)).thenReturn(Map.of());
    when(runtimeStateService.loadItemState(device)).thenReturn(Map.of());
    when(icmpExecutor.supports(any())).thenReturn(false);
    when(snmpExecutor.supports(any())).thenReturn(false);
    when(derivedExecutor.supports(any())).thenReturn(false);

    service.collectAll();

    @SuppressWarnings("unchecked")
    ArgumentCaptor<com.networkscanner.backend.monitoring.dto.PolledMetricsEvent> eventCaptor =
        ArgumentCaptor.forClass(com.networkscanner.backend.monitoring.dto.PolledMetricsEvent.class);
    verify(metricsPublisher).publish(eventCaptor.capture());
    boolean hasUnavailable = eventCaptor.getValue().values().stream()
        .anyMatch(
            value -> "zabbix[host,snmp,available]".equals(value.itemKey())
                && value.numericValue() != null
                && value.numericValue() == 0.0d
        );
    assertTrue(hasUnavailable);
    verify(snmpExecutor, never()).executeBatch(any(), any(), any(), any(), any());
  }

  private static MonitoredDeviceEntity unreachableDevice() {
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(43L);
    device.setIp("192.168.51.43");
    device.setTemplateId("tpl");
    device.setTemplateIds("tpl");
    return device;
  }

  private static MonitoredDeviceEntity reachableDevice() {
    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(42L);
    device.setIp("192.168.51.42");
    device.setTemplateId("tpl");
    device.setTemplateIds("tpl");
    return device;
  }

  private static ResolvedMonitoringTemplate templateWithSnmpAvailabilityItem() {
    ZabbixItemRuntime snmpAvailable = new ZabbixItemRuntime(
        "uuid-snmp-available",
        "zabbix[host,snmp,available]",
        "SNMP availability",
        "INTERNAL",
        null,
        30,
        "UNSIGNED",
        "",
        "",
        null,
        "",
        "",
        List.of(),
        null,
        false,
        null
    );
    ZabbixItemRuntime snmpAgent = new ZabbixItemRuntime(
        "uuid-snmp-agent",
        "agent.key",
        "Agent",
        "SNMP_AGENT",
        ".1.3.6.1.2.1.1.1.0",
        30,
        "FLOAT",
        "%",
        "",
        null,
        "",
        "",
        List.of(),
        null,
        false,
        null
    );
    return baseTemplate(Map.of(
        "zabbix[host,snmp,available]", snmpAvailable,
        "agent.key", snmpAgent
    ));
  }

  private static ResolvedMonitoringTemplate templateWithSnmpAgentItem() {
    ZabbixItemRuntime snmpAgent = new ZabbixItemRuntime(
        "uuid-snmp-agent",
        "agent.key",
        "Agent",
        "SNMP_AGENT",
        ".1.3.6.1.2.1.1.1.0",
        30,
        "FLOAT",
        "%",
        "",
        null,
        "",
        "",
        List.of(),
        null,
        false,
        null
    );
    return baseTemplate(Map.of("agent.key", snmpAgent));
  }

  private static ResolvedMonitoringTemplate baseTemplate(Map<String, ZabbixItemRuntime> items) {
    return new ResolvedMonitoringTemplate(
        "tpl",
        "zabbix",
        "Template",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        null,
        null,
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        items,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
  }
}
