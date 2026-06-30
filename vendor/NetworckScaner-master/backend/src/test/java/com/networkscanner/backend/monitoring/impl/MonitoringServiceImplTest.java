package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.inventory.api.ConfigBackupService;
import com.networkscanner.backend.monitoring.api.MetricsHistoryService;
import com.networkscanner.backend.monitoring.api.MonitoredDeviceItemService;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.DeviceInterfaceDto;
import com.networkscanner.backend.monitoring.dto.DeviceMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.MetricDefinition;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.ItemStateTelemetrySnapshot;
import com.networkscanner.backend.monitoring.dto.MonitoringDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringItemStateDto;
import com.networkscanner.backend.monitoring.dto.MonitoringItemStatePageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricDto;
import com.networkscanner.backend.monitoring.dto.MonitoringHostFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringHostPageDto;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.UnitDefinition;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixGraphItemRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixGraphRecord;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceInterfaceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceInterfaceRepository;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import com.networkscanner.backend.monitoring.repository.MonitoringTelemetrySnapshotRepository;
import com.networkscanner.backend.monitoring.repository.UploadedMonitoringTemplateRepository;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class MonitoringServiceImplTest {

  @Test
  void listReturnsPagedStoredDevicesWithoutTriggeringNetworkRefresh() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository = mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);
    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(7L);
    entity.setIp("10.10.10.7");
    entity.setHostName("device-7");
    entity.setName("Device 7");
    entity.setSerialNumber("SN-7");
    entity.setMacAddress("AA:BB:CC:DD:EE:07");
    entity.setVendor("Cisco");
    entity.setModel("ISR");
    entity.setFirmwareVersion("1.0");
    entity.setPollingStatus("Активен");
    entity.setStatus("Включено");
    entity.setHealthStatus(DeviceHealthStatus.NORM);
    entity.setGroupName("Core");
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    when(monitoredDeviceRepository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));
    when(monitoredDeviceRepository.count(any(Specification.class))).thenReturn(1L, 0L, 0L);

    AuditLogService auditLogService = mock(AuditLogService.class);
    MonitoringServiceImpl service = new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService
    );

    MonitoringHostPageDto result = service.list(
        new MonitoringHostFilter(null, null, null, null, null, null, null),
        0,
        15,
        "ip",
        "asc"
    );

    assertEquals(1, result.content().size());
    assertEquals("10.10.10.7", result.content().get(0).ip());
    assertEquals(1L, result.availableCount());
    verify(monitoredDeviceRepository).findAll(any(Specification.class), any(Pageable.class));
    verifyNoInteractions(scanService);
  }

  @Test
  void getDeviceInterfacesByIdPersistsOneRowPerInterfaceNameWhenScanReturnsDuplicates() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository = mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);
    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(2L);
    entity.setIp("10.10.10.2");
    entity.setHostName("device-2");
    entity.setName("Device 2");
    entity.setSerialNumber("SN-2");
    entity.setMacAddress("AA:BB:CC:DD:EE:02");
    entity.setVendor("Cisco");
    entity.setModel("ISR");
    entity.setFirmwareVersion("1.0");
    entity.setPollingStatus("Активен");
    entity.setStatus("Включено");
    entity.setHealthStatus(DeviceHealthStatus.NORM);
    entity.setGroupName("Core");
    entity.setAvailabilityJson("[]");
    entity.setTemplateId("tpl");
    entity.setTemplateIds("tpl");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(monitoredDeviceRepository.findById(2L)).thenReturn(Optional.of(entity));
    when(monitoredDeviceInterfaceRepository.findByDevice_IdOrderByNameAsc(2L)).thenReturn(List.of());
    when(templateResolver.resolveForDevice(eq(List.of("tpl")), eq("Cisco"), eq("ISR"), eq("1.0"))).thenReturn(template);
    DeviceInterfaceDto first = new DeviceInterfaceDto(
        "BR0/0", "first", "UP", "UP", "Нет", "1 Gbit/s", "100 Mbit/s", "WAN", "routed", "ethernet");
    DeviceInterfaceDto second = new DeviceInterfaceDto(
        "BR0/0", "second", "UP", "UP", "Нет", "1 Gbit/s", "200 Mbit/s", "WAN", "routed", "ethernet");
    when(scanService.readInterfaces(eq("10.10.10.2"), eq(template))).thenReturn(List.of(first, second));
    when(monitoredDeviceInterfaceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));

    AuditLogService auditLogService = mock(AuditLogService.class);
    MonitoringServiceImpl service = new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService
    );

    List<DeviceInterfaceDto> result = service.getDeviceInterfacesById(2L);

    @SuppressWarnings("unchecked")
    ArgumentCaptor<List<MonitoredDeviceInterfaceEntity>> captor = ArgumentCaptor.forClass(List.class);
    verify(monitoredDeviceInterfaceRepository).saveAll(captor.capture());
    List<MonitoredDeviceInterfaceEntity> saved = captor.getValue();
    assertEquals(1, saved.size());
    assertEquals("BR0/0", saved.get(0).getName());
    assertEquals("second", saved.get(0).getDescription());
    assertEquals(1, result.size());
    assertEquals("second", result.get(0).description());
  }

  @Test
  void getMetricsWithUnitsByIdResolvesDisplayNameFromItemPrototypeAndDiscoveryMacros() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository = mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);

    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    OffsetDateTime from = now.minusHours(1);

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(11L);
    entity.setIp("10.10.10.11");
    entity.setHostName("device-11");
    entity.setName("Device 11");
    entity.setVendor("Linux");
    entity.setModel("Server");
    entity.setTemplateId("tpl-linux");
    entity.setTemplateIds("tpl-linux");
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    String prototypeKey = "vfs.dev.read.rate[{#DEVNAME}]";
    String metricKey = "vfs.dev.read.rate[sda]";
    String prototypeName = "{#DEVNAME}: Disk read rate";

    ZabbixItemRuntime prototype = new ZabbixItemRuntime(
        "item-uuid",
        prototypeKey,
        prototypeName,
        "DEPENDENT",
        null,
        60,
        "FLOAT",
        "Bps",
        null,
        "vfs.dev.walk",
        null,
        null,
        List.of(),
        null,
        true,
        "vfs.dev.discovery"
    );

    ZabbixDiscoveryRuleRuntime discoveryRule = new ZabbixDiscoveryRuleRuntime(
        "rule-uuid",
        "vfs.dev.discovery",
        "Block devices discovery",
        "DEPENDENT",
        null,
        "vfs.dev.walk",
        List.of(),
        List.of(),
        3600,
        86400,
        null,
        List.of(prototype),
        List.of(),
        List.of()
    );

    MetricDefinition metricDefinition = new MetricDefinition(null, "Bps", null, null, null, prototypeName);
    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.metrics()).thenReturn(Map.of(prototypeKey, metricDefinition));
    when(template.discoveryRules()).thenReturn(Map.of("vfs.dev.discovery", discoveryRule));

    when(monitoredDeviceRepository.findById(11L)).thenReturn(Optional.of(entity));
    when(templateResolver.resolveForDevice(eq(List.of("tpl-linux")), eq("Linux"), eq("Server"), eq(null))).thenReturn(template);
    when(metricsHistoryService.queryMetricValues(eq("10.10.10.11"), eq(from), eq(now), eq(null)))
        .thenReturn(List.of(new MetricValueDto(now, "10.10.10.11", metricKey, 123.0d, "Bps", null)));
    when(runtimeStateService.loadActiveDiscoveryInstances(eq(entity)))
        .thenReturn(Map.of(
            "vfs.dev.discovery",
            List.of(new DiscoveryInstanceRuntime(
                "vfs.dev.discovery",
                "instance-sda",
                Map.of("{#DEVNAME}", "sda"),
                now.minusMinutes(5),
                now.plusHours(1)
            ))
        ));

    AuditLogService auditLogService = mock(AuditLogService.class);
    MonitoringServiceImpl service = new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService
    );

    List<MetricValueDto> result = service.getMetricsWithUnitsById(11L, from, now, null);

    assertEquals(1, result.size());
    assertEquals(metricKey, result.get(0).metricName());
    assertEquals("sda: Disk read rate", result.get(0).metricDisplayName());
    assertEquals(123.0d, result.get(0).scaledMetricValue(), 0.0001d);
    assertEquals("Bps", result.get(0).scaledUnit());
  }

  @Test
  void getItemStateByDeviceIdResolvesItemDisplayNameFromTemplate() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository = mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);

    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(12L);
    entity.setIp("10.10.10.12");
    entity.setHostName("device-12");
    entity.setName("Device 12");
    entity.setVendor("Linux");
    entity.setModel("Server");
    entity.setTemplateId("tpl-linux");
    entity.setTemplateIds("tpl-linux");
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    String itemKey = "system.uptime";
    String itemDisplayName = "Uptime";

    MetricDefinition metricDefinition = new MetricDefinition(null, "uptime", null, null, null, itemDisplayName);
    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.metrics()).thenReturn(Map.of(itemKey, metricDefinition));
    when(template.discoveryRules()).thenReturn(Map.of());

    when(monitoredDeviceRepository.findById(12L)).thenReturn(Optional.of(entity));
    when(templateResolver.resolveForDevice(eq(List.of("tpl-linux")), eq("Linux"), eq("Server"), eq(null)))
        .thenReturn(template);
    when(templateResolver.mapValue(any(), any(), any())).thenAnswer(invocation -> invocation.getArgument(2));
    when(runtimeStateService.loadItemStateList(eq(entity)))
        .thenReturn(List.of(new ItemStateSnapshot(
            "tpl-linux",
            itemKey,
            null,
            3600.0d,
            null,
            "uptime",
            null,
            "ok",
            null,
            now
        )));

    AuditLogService auditLogService = mock(AuditLogService.class);
    MonitoringServiceImpl service = new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService
    );

    MonitoringItemStatePageDto result = service.getItemStatePage(12L, null, 0, 0);

    assertEquals(1, result.content().size());
    assertEquals(itemKey, result.content().get(0).itemKey());
    assertEquals(itemDisplayName, result.content().get(0).itemDisplayName());
  }

  @Test
  void getMetricsHistoryByIdBuildsPanelsFromGraphPrototypesAndFallbacks() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository = mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);

    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    OffsetDateTime from = now.minusHours(1);

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(21L);
    entity.setIp("10.10.10.21");
    entity.setHostName("device-21");
    entity.setName("Device 21");
    entity.setVendor("Linux");
    entity.setModel("Server");
    entity.setTemplateId("tpl-linux");
    entity.setTemplateIds("tpl-linux");
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    ZabbixGraphItemRecord inItem = new ZabbixGraphItemRecord(
        "0",
        null,
        "199C0D",
        null,
        null,
        null,
        new ZabbixGraphItemRecord.ZabbixGraphItemTarget("Linux by SNMP", "net.if.in[{#IFNAME}]")
    );
    ZabbixGraphItemRecord outItem = new ZabbixGraphItemRecord(
        "1",
        null,
        "F63100",
        null,
        "RIGHT",
        null,
        new ZabbixGraphItemRecord.ZabbixGraphItemTarget("Linux by SNMP", "net.if.out[{#IFNAME}]")
    );
    ZabbixGraphRecord graphPrototype = new ZabbixGraphRecord(
        "graph-uuid",
        "Interface {#IFNAME}: Network traffic",
        "NORMAL",
        null,
        List.of(inItem, outItem)
    );
    ZabbixDiscoveryRuleRuntime discoveryRule = new ZabbixDiscoveryRuleRuntime(
        "rule-uuid",
        "net.if.discovery",
        "Network interfaces discovery",
        "DEPENDENT",
        null,
        "net.if.walk",
        List.of(),
        List.of(),
        3600,
        86400,
        null,
        List.of(),
        List.of(),
        List.of(graphPrototype)
    );

    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.metrics()).thenReturn(Map.of(
        "net.if.in[{#IFNAME}]", new MetricDefinition(null, "Bps", null, null, null, "Inbound traffic"),
        "net.if.out[{#IFNAME}]", new MetricDefinition(null, "Bps", null, null, null, "Outbound traffic"),
        "system.cpu.util", new MetricDefinition(null, "%", null, null, null, "CPU utilization")
    ));
    when(template.discoveryRules()).thenReturn(Map.of("net.if.discovery", discoveryRule));
    when(template.graphs()).thenReturn(List.of());

    when(monitoredDeviceRepository.findById(21L)).thenReturn(Optional.of(entity));
    when(templateResolver.resolveForDevice(eq(List.of("tpl-linux")), eq("Linux"), eq("Server"), eq(null))).thenReturn(template);
    when(metricsHistoryService.listMetricNamesInRange(eq("10.10.10.21"), eq(from), eq(now)))
        .thenReturn(List.of("net.if.in[eth0]", "net.if.out[eth0]", "system.cpu.util"));
    when(metricsHistoryService.queryMetricValues(eq("10.10.10.21"), eq(from), eq(now), anyCollection(), any()))
        .thenReturn(List.of(
            new MetricValueDto(now.minusMinutes(3), "10.10.10.21", "net.if.in[eth0]", 100.0d, "bps", null),
            new MetricValueDto(now.minusMinutes(2), "10.10.10.21", "net.if.out[eth0]", 90.0d, "bps", null),
            new MetricValueDto(now.minusMinutes(1), "10.10.10.21", "system.cpu.util", 34.0d, "%", null)
        ));
    when(runtimeStateService.loadActiveDiscoveryInstances(eq(entity)))
        .thenReturn(Map.of(
            "net.if.discovery",
            List.of(new DiscoveryInstanceRuntime(
                "net.if.discovery",
                "if-eth0",
                Map.of("{#IFNAME}", "eth0"),
                now.minusMinutes(5),
                now.plusHours(1)
            ))
        ));

    AuditLogService auditLogService2 = mock(AuditLogService.class);
    MonitoringServiceImpl service = new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService2
    );

    DeviceMetricsHistoryResponseDto response = service.getMetricsHistoryById(21L, from, now, null, null, null, null, null);

    assertEquals(2, response.totalChartPanels());
    assertEquals(2, response.chartPanels().size());
    assertEquals("Interface eth0: Network traffic", response.chartPanels().get(0).title());
    assertEquals(List.of("net.if.in[eth0]", "net.if.out[eth0]"), response.chartPanels().get(0).metricNames());
    assertEquals(List.of("net.if.out[eth0]"), response.chartPanels().get(0).rightAxisMetricNames());
    assertEquals(2, response.chartPanels().get(0).points().size());
    assertEquals(List.of("system.cpu.util"), response.chartPanels().get(1).metricNames());
    assertEquals(1, response.chartPanels().get(1).points().size());
    assertEquals("bps", response.chartPanels().get(0).points().get(0).scaledUnit());
  }

  @Test
  void getMetricsHistoryByIdBuildsGraphPrototypesWhenDiscoveryTableEmpty() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository = mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);

    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    OffsetDateTime from = now.minusHours(1);

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(22L);
    entity.setIp("10.10.10.22");
    entity.setHostName("device-22");
    entity.setName("Device 22");
    entity.setVendor("Linux");
    entity.setModel("Server");
    entity.setTemplateId("tpl-linux");
    entity.setTemplateIds("tpl-linux");
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    ZabbixGraphItemRecord inItem = new ZabbixGraphItemRecord(
        "0",
        null,
        "199C0D",
        null,
        null,
        null,
        new ZabbixGraphItemRecord.ZabbixGraphItemTarget("Linux by SNMP", "net.if.in[{#IFNAME}]")
    );
    ZabbixGraphItemRecord outItem = new ZabbixGraphItemRecord(
        "1",
        null,
        "F63100",
        null,
        "RIGHT",
        null,
        new ZabbixGraphItemRecord.ZabbixGraphItemTarget("Linux by SNMP", "net.if.out[{#IFNAME}]")
    );
    ZabbixGraphRecord graphPrototype = new ZabbixGraphRecord(
        "graph-uuid",
        "Interface {#IFNAME}: Network traffic",
        "NORMAL",
        null,
        List.of(inItem, outItem)
    );
    ZabbixDiscoveryRuleRuntime discoveryRule = new ZabbixDiscoveryRuleRuntime(
        "rule-uuid",
        "net.if.discovery",
        "Network interfaces discovery",
        "DEPENDENT",
        null,
        "net.if.walk",
        List.of(),
        List.of(),
        3600,
        86400,
        null,
        List.of(),
        List.of(),
        List.of(graphPrototype)
    );

    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.metrics()).thenReturn(Map.of(
        "net.if.in[{#IFNAME}]", new MetricDefinition(null, "Bps", null, null, null, "Inbound traffic"),
        "net.if.out[{#IFNAME}]", new MetricDefinition(null, "Bps", null, null, null, "Outbound traffic"),
        "system.cpu.util", new MetricDefinition(null, "%", null, null, null, "CPU utilization")
    ));
    when(template.discoveryRules()).thenReturn(Map.of("net.if.discovery", discoveryRule));
    when(template.graphs()).thenReturn(List.of());

    when(monitoredDeviceRepository.findById(22L)).thenReturn(Optional.of(entity));
    when(templateResolver.resolveForDevice(eq(List.of("tpl-linux")), eq("Linux"), eq("Server"), eq(null))).thenReturn(template);
    when(metricsHistoryService.listMetricNamesInRange(eq("10.10.10.22"), eq(from), eq(now)))
        .thenReturn(List.of("net.if.in[eth0]", "net.if.out[eth0]", "system.cpu.util"));
    when(metricsHistoryService.queryMetricValues(eq("10.10.10.22"), eq(from), eq(now), anyCollection(), any()))
        .thenReturn(List.of(
            new MetricValueDto(now.minusMinutes(3), "10.10.10.22", "net.if.in[eth0]", 100.0d, "bps", null),
            new MetricValueDto(now.minusMinutes(2), "10.10.10.22", "net.if.out[eth0]", 90.0d, "bps", null),
            new MetricValueDto(now.minusMinutes(1), "10.10.10.22", "system.cpu.util", 34.0d, "%", null)
        ));
    when(runtimeStateService.loadActiveDiscoveryInstances(eq(entity))).thenReturn(Map.of());

    AuditLogService auditLogService2 = mock(AuditLogService.class);
    MonitoringServiceImpl service = new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService2
    );

    DeviceMetricsHistoryResponseDto response = service.getMetricsHistoryById(22L, from, now, null, null, null, null, null);

    assertEquals(2, response.totalChartPanels());
    assertEquals(2, response.chartPanels().size());
    assertEquals("Interface eth0: Network traffic", response.chartPanels().get(0).title());
    assertEquals(List.of("net.if.in[eth0]", "net.if.out[eth0]"), response.chartPanels().get(0).metricNames());
    assertEquals(List.of("net.if.out[eth0]"), response.chartPanels().get(0).rightAxisMetricNames());
    assertEquals(2, response.chartPanels().get(0).points().size());
    assertEquals(List.of("system.cpu.util"), response.chartPanels().get(1).metricNames());
    assertEquals(1, response.chartPanels().get(1).points().size());
  }

  @Test
  void getMetricsHistoryByIdIncludesStaticPieGraph() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository = mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);

    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    OffsetDateTime from = now.minusHours(1);

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(24L);
    entity.setIp("10.10.10.24");
    entity.setHostName("device-24");
    entity.setName("Device 24");
    entity.setVendor("Linux");
    entity.setModel("Server");
    entity.setTemplateId("tpl-linux");
    entity.setTemplateIds("tpl-linux");
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    ZabbixGraphItemRecord sliceA = new ZabbixGraphItemRecord(
        "0",
        null,
        "199C0D",
        null,
        null,
        null,
        new ZabbixGraphItemRecord.ZabbixGraphItemTarget("Linux by SNMP", "pie.slice.a")
    );
    ZabbixGraphItemRecord sliceB = new ZabbixGraphItemRecord(
        "1",
        null,
        "F63100",
        null,
        null,
        null,
        new ZabbixGraphItemRecord.ZabbixGraphItemTarget("Linux by SNMP", "pie.slice.b")
    );
    ZabbixGraphRecord pieGraph = new ZabbixGraphRecord(
        "pie-uuid",
        "Круговая диаграмма (тест)",
        "PIE",
        null,
        List.of(sliceA, sliceB)
    );

    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.metrics()).thenReturn(Map.of(
        "pie.slice.a", new MetricDefinition(null, "B", null, null, null, "Сектор A"),
        "pie.slice.b", new MetricDefinition(null, "B", null, null, null, "Сектор B")
    ));
    when(template.discoveryRules()).thenReturn(Map.of());
    when(template.graphs()).thenReturn(List.of(pieGraph));

    when(monitoredDeviceRepository.findById(24L)).thenReturn(Optional.of(entity));
    when(templateResolver.resolveForDevice(eq(List.of("tpl-linux")), eq("Linux"), eq("Server"), eq(null))).thenReturn(template);
    when(metricsHistoryService.listMetricNamesInRange(eq("10.10.10.24"), eq(from), eq(now)))
        .thenReturn(List.of("pie.slice.a", "pie.slice.b"));
    when(metricsHistoryService.queryMetricValues(eq("10.10.10.24"), eq(from), eq(now), anyCollection(), any()))
        .thenReturn(List.of(
            new MetricValueDto(now.minusMinutes(2), "10.10.10.24", "pie.slice.a", 30.0d, "B", null),
            new MetricValueDto(now.minusMinutes(1), "10.10.10.24", "pie.slice.b", 70.0d, "B", null)
        ));
    when(runtimeStateService.loadActiveDiscoveryInstances(eq(entity))).thenReturn(Map.of());

    AuditLogService auditLogService2 = mock(AuditLogService.class);
    MonitoringServiceImpl service = new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService2
    );

    DeviceMetricsHistoryResponseDto response = service.getMetricsHistoryById(24L, from, now, null, null, null, null, null);

    assertEquals(1, response.totalChartPanels());
    assertEquals(1, response.chartPanels().size());
    assertEquals(2, response.chartPanels().get(0).points().size());
    assertEquals("PIE", response.chartPanels().get(0).graphType());
    assertEquals("Круговая диаграмма (тест)", response.chartPanels().get(0).title());
    assertEquals(List.of("pie.slice.a", "pie.slice.b"), response.chartPanels().get(0).metricNames());
  }

  @Test
  void getMetricsHistoryByIdSlicesChartPanelsWindow() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository = mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);

    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    OffsetDateTime from = now.minusHours(1);

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(23L);
    entity.setIp("10.10.10.23");
    entity.setHostName("device-23");
    entity.setName("Device 23");
    entity.setVendor("Linux");
    entity.setModel("Server");
    entity.setTemplateId("tpl-linux");
    entity.setTemplateIds("tpl-linux");
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    ZabbixGraphItemRecord inItem = new ZabbixGraphItemRecord(
        "0",
        null,
        "199C0D",
        null,
        null,
        null,
        new ZabbixGraphItemRecord.ZabbixGraphItemTarget("Linux by SNMP", "net.if.in[{#IFNAME}]")
    );
    ZabbixGraphItemRecord outItem = new ZabbixGraphItemRecord(
        "1",
        null,
        "F63100",
        null,
        "RIGHT",
        null,
        new ZabbixGraphItemRecord.ZabbixGraphItemTarget("Linux by SNMP", "net.if.out[{#IFNAME}]")
    );
    ZabbixGraphRecord graphPrototype = new ZabbixGraphRecord(
        "graph-uuid",
        "Interface {#IFNAME}: Network traffic",
        "NORMAL",
        null,
        List.of(inItem, outItem)
    );
    ZabbixDiscoveryRuleRuntime discoveryRule = new ZabbixDiscoveryRuleRuntime(
        "rule-uuid",
        "net.if.discovery",
        "Network interfaces discovery",
        "DEPENDENT",
        null,
        "net.if.walk",
        List.of(),
        List.of(),
        3600,
        86400,
        null,
        List.of(),
        List.of(),
        List.of(graphPrototype)
    );

    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.metrics()).thenReturn(Map.of(
        "net.if.in[{#IFNAME}]", new MetricDefinition(null, "Bps", null, null, null, "Inbound traffic"),
        "net.if.out[{#IFNAME}]", new MetricDefinition(null, "Bps", null, null, null, "Outbound traffic"),
        "system.cpu.util", new MetricDefinition(null, "%", null, null, null, "CPU utilization")
    ));
    when(template.discoveryRules()).thenReturn(Map.of("net.if.discovery", discoveryRule));
    when(template.graphs()).thenReturn(List.of());

    when(monitoredDeviceRepository.findById(23L)).thenReturn(Optional.of(entity));
    when(templateResolver.resolveForDevice(eq(List.of("tpl-linux")), eq("Linux"), eq("Server"), eq(null))).thenReturn(template);
    when(metricsHistoryService.listMetricNamesInRange(eq("10.10.10.23"), eq(from), eq(now)))
        .thenReturn(List.of("net.if.in[eth0]", "net.if.out[eth0]", "system.cpu.util"));
    when(metricsHistoryService.queryMetricValues(eq("10.10.10.23"), eq(from), eq(now), anyCollection(), any()))
        .thenAnswer(invocation -> {
          java.util.Collection<String> requested = invocation.getArgument(3);
          List<MetricValueDto> all = List.of(
              new MetricValueDto(now.minusMinutes(3), "10.10.10.23", "net.if.in[eth0]", 100.0d, "bps", null),
              new MetricValueDto(now.minusMinutes(2), "10.10.10.23", "net.if.out[eth0]", 90.0d, "bps", null),
              new MetricValueDto(now.minusMinutes(1), "10.10.10.23", "system.cpu.util", 34.0d, "%", null)
          );
          return all.stream().filter(p -> requested.contains(p.metricName())).toList();
        });
    when(runtimeStateService.loadActiveDiscoveryInstances(eq(entity)))
        .thenReturn(Map.of(
            "net.if.discovery",
            List.of(new DiscoveryInstanceRuntime(
                "net.if.discovery",
                "if-eth0",
                Map.of("{#IFNAME}", "eth0"),
                now.minusMinutes(5),
                now.plusHours(1)
            ))
        ));

    AuditLogService auditLogService2 = mock(AuditLogService.class);
    MonitoringServiceImpl service = new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService2
    );

    DeviceMetricsHistoryResponseDto first = service.getMetricsHistoryById(23L, from, now, null, null, 0, 1, null);
    assertEquals(2, first.totalChartPanels());
    assertEquals(1, first.chartPanels().size());
    assertEquals(2, first.chartPanels().get(0).points().size());

    DeviceMetricsHistoryResponseDto second = service.getMetricsHistoryById(23L, from, now, null, null, 1, 1, null);
    assertEquals(2, second.totalChartPanels());
    assertEquals(1, second.chartPanels().size());
    assertEquals(List.of("system.cpu.util"), second.chartPanels().get(0).metricNames());
    assertEquals(1, second.chartPanels().get(0).points().size());

    // Точки читаются только для метрик панелей текущего среза, а не по всему устройству.
    @SuppressWarnings("unchecked")
    ArgumentCaptor<java.util.Collection<String>> metricsCaptor = ArgumentCaptor.forClass(java.util.Collection.class);
    verify(metricsHistoryService, org.mockito.Mockito.times(2))
        .queryMetricValues(eq("10.10.10.23"), eq(from), eq(now), metricsCaptor.capture(), any());
    java.util.List<java.util.Collection<String>> captured = metricsCaptor.getAllValues();
    assertEquals(java.util.Set.of("net.if.in[eth0]", "net.if.out[eth0]"), new java.util.HashSet<>(captured.get(0)));
    assertEquals(java.util.Set.of("system.cpu.util"), new java.util.HashSet<>(captured.get(1)));
  }

  @Test
  void getMetricsWithUnitsByIdAppliesScalingFromTemplateWhenHistoryUnitMissing() {
    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    OffsetDateTime from = now.minusHours(1);
    String metricKey = "net.if.in[eth0]";

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(12L);
    entity.setIp("10.10.10.12");
    entity.setHostName("host-12");
    entity.setName("Device 12");
    entity.setTemplateId("tpl-net");
    entity.setEffectiveTemplateId("tpl-net");
    entity.setVendor("Cisco");
    entity.setModel("ISR");
    entity.setFirmwareVersion("1.0");
    entity.setStatus("Включено");
    entity.setHealthStatus(DeviceHealthStatus.NORM);
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);

    when(monitoredDeviceRepository.findById(12L)).thenReturn(Optional.of(entity));
    when(metricsHistoryService.queryMetricValues(eq("10.10.10.12"), eq(from), eq(now), eq(null)))
        .thenReturn(List.of(new MetricValueDto(now, "10.10.10.12", metricKey, 25_000_000.0, null, null)));

    MetricDefinition metricDefinition = new MetricDefinition(null, "bps", null, null, null, "Inbound");
    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.metrics()).thenReturn(Map.of(metricKey, metricDefinition));
    when(template.units()).thenReturn(Map.of("bps", new UnitDefinition("bps", "bits per second")));
    when(template.discoveryRules()).thenReturn(Map.of());
    when(template.graphs()).thenReturn(List.of());
    when(template.items()).thenReturn(Map.of());
    when(templateResolver.resolveForDevice(eq(List.of("tpl-net")), eq("Cisco"), eq("ISR"), eq("1.0")))
        .thenReturn(template);
    when(runtimeStateService.loadItemStateList(eq(entity))).thenReturn(List.of());

    MonitoringServiceImpl service = monitoringServiceWithMocks(
        mock(SnmpScanService.class),
        mock(ConfigBackupService.class),
        metricsHistoryService,
        mock(MonitoredDeviceInterfaceRepository.class),
        monitoredDeviceRepository,
        mock(MonitoringEventRepository.class),
        mock(MonitoringTelemetrySnapshotRepository.class),
        mock(UploadedMonitoringTemplateRepository.class),
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        mock(MonitoringTemplateArchiveReader.class),
        runtimeStateService,
        mock(MonitoredDeviceItemService.class),
        mock(AuditLogService.class)
    );

    List<MetricValueDto> result = service.getMetricsWithUnitsById(12L, from, now, null);

    assertEquals(1, result.size());
    assertEquals(metricKey, result.get(0).metricName());
    assertEquals("bps", result.get(0).unit());
    assertEquals("Mbps", result.get(0).scaledUnit());
    assertEquals(25.0d, result.get(0).scaledMetricValue(), 0.0001d);
  }

  @Test
  void getMetricsWithUnitsByIdFallsBackToItemStateUnitWhenHistoryAndTemplateMissing() {
    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");
    OffsetDateTime from = now.minusHours(1);
    String metricKey = "custom.metric";

    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(13L);
    entity.setIp("10.10.10.13");
    entity.setTemplateId("tpl-custom");
    entity.setEffectiveTemplateId("tpl-custom");
    entity.setVendor("Cisco");
    entity.setModel("ISR");
    entity.setFirmwareVersion("1.0");
    entity.setStatus("Включено");
    entity.setHealthStatus(DeviceHealthStatus.NORM);
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));
    entity.setUpdatedAt(now);

    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);

    when(monitoredDeviceRepository.findById(13L)).thenReturn(Optional.of(entity));
    when(metricsHistoryService.queryMetricValues(eq("10.10.10.13"), eq(from), eq(now), eq(null)))
        .thenReturn(List.of(new MetricValueDto(now, "10.10.10.13", metricKey, 1_500_000.0, null, null)));

    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.metrics()).thenReturn(Map.of());
    when(template.units()).thenReturn(Map.of());
    when(template.discoveryRules()).thenReturn(Map.of());
    when(template.graphs()).thenReturn(List.of());
    when(templateResolver.resolveForDevice(anyList(), anyString(), anyString(), isNull())).thenReturn(template);
    when(runtimeStateService.loadItemStateList(eq(entity)))
        .thenReturn(List.of(new ItemStateSnapshot(
            "tpl-custom",
            metricKey,
            "",
            1_500_000.0,
            null,
            "bps",
            null,
            null,
            null,
            now
        )));

    MonitoringServiceImpl service = monitoringServiceWithMocks(
        mock(SnmpScanService.class),
        mock(ConfigBackupService.class),
        metricsHistoryService,
        mock(MonitoredDeviceInterfaceRepository.class),
        monitoredDeviceRepository,
        mock(MonitoringEventRepository.class),
        mock(MonitoringTelemetrySnapshotRepository.class),
        mock(UploadedMonitoringTemplateRepository.class),
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        mock(MonitoringTemplateArchiveReader.class),
        runtimeStateService,
        mock(MonitoredDeviceItemService.class),
        mock(AuditLogService.class)
    );

    List<MetricValueDto> result = service.getMetricsWithUnitsById(13L, from, now, null);

    assertEquals(1, result.size());
    assertEquals("bps", result.get(0).unit());
    assertEquals("Mbps", result.get(0).scaledUnit());
  }

  @Test
  void deactivatePurgesDeviceHistoryByIpBeforeDelete() {
    MonitoredDeviceEntity entity = monitoredDevice("10.10.10.55", 55L);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    when(monitoredDeviceRepository.findAllByIpIn(List.of("10.10.10.55"))).thenReturn(List.of(entity));
    when(monitoredDeviceRepository.findAll()).thenReturn(List.of());

    MonitoringServiceImpl service = monitoringServiceWithMocks(
        mock(SnmpScanService.class),
        mock(ConfigBackupService.class),
        mock(MetricsHistoryService.class),
        mock(MonitoredDeviceInterfaceRepository.class),
        monitoredDeviceRepository,
        mock(MonitoringEventRepository.class),
        mock(MonitoringTelemetrySnapshotRepository.class),
        mock(UploadedMonitoringTemplateRepository.class),
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        mock(MonitoringTemplateResolver.class),
        mock(MonitoringTemplateArchiveReader.class),
        runtimeStateService,
        mock(MonitoredDeviceItemService.class),
        mock(AuditLogService.class)
    );

    service.deactivate(List.of("10.10.10.55"), null);

    InOrder order = inOrder(runtimeStateService, monitoredDeviceRepository);
    order.verify(runtimeStateService).purgeDeviceHistory(List.of("10.10.10.55"));
    order.verify(monitoredDeviceRepository).deleteAll(List.of(entity));
  }

  @Test
  void deactivateByIdsPurgesDeviceHistoryBeforeDelete() {
    MonitoredDeviceEntity entity = monitoredDevice("10.10.10.56", 56L);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    when(monitoredDeviceRepository.findAllById(List.of(56L))).thenReturn(List.of(entity));
    when(monitoredDeviceRepository.findAll()).thenReturn(List.of());

    MonitoringServiceImpl service = monitoringServiceWithMocks(
        mock(SnmpScanService.class),
        mock(ConfigBackupService.class),
        mock(MetricsHistoryService.class),
        mock(MonitoredDeviceInterfaceRepository.class),
        monitoredDeviceRepository,
        mock(MonitoringEventRepository.class),
        mock(MonitoringTelemetrySnapshotRepository.class),
        mock(UploadedMonitoringTemplateRepository.class),
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        mock(MonitoringTemplateResolver.class),
        mock(MonitoringTemplateArchiveReader.class),
        runtimeStateService,
        mock(MonitoredDeviceItemService.class),
        mock(AuditLogService.class)
    );

    service.deactivateByIds(List.of(56L), null);

    verify(runtimeStateService).purgeDeviceHistory(List.of("10.10.10.56"));
    verify(monitoredDeviceRepository).deleteAll(List.of(entity));
  }

  @Test
  void refreshDeviceMonitoringDetailsFillsTelemetryGapsFromItemState() {
    SnmpScanService scanService = mock(SnmpScanService.class);
    ConfigBackupService configBackupService = mock(ConfigBackupService.class);
    MetricsHistoryService metricsHistoryService = mock(MetricsHistoryService.class);
    MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository =
        mock(MonitoredDeviceInterfaceRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);
    MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository =
        mock(MonitoringTelemetrySnapshotRepository.class);
    UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository =
        mock(UploadedMonitoringTemplateRepository.class);
    MonitoringTemplateResolver templateResolver = mock(MonitoringTemplateResolver.class);
    MonitoringTemplateArchiveReader templateArchiveReader = mock(MonitoringTemplateArchiveReader.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoredDeviceItemService monitoredDeviceItemService = mock(MonitoredDeviceItemService.class);

    OffsetDateTime now = OffsetDateTime.parse("2026-06-01T12:00:00Z");
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(99L);
    entity.setIp("10.10.10.99");
    entity.setHostName("cisco-99");
    entity.setName("Cisco 99");
    entity.setVendor("Cisco");
    entity.setModel("ISR");
    entity.setTemplateId("cisco-ios-by-snmp");
    entity.setTemplateIds("cisco-ios-by-snmp");
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(now.minusDays(1));

    String cpuItemKey = "system.cpu.util[cpmCPUTotal5minRev.1]";
    MonitoringDetailsDto emptySnmp = new MonitoringDetailsDto(
        new MonitoringMetricDto(null, null, null, null, null, null),
        null,
        null,
        "-",
        "-",
        "-",
        "-",
        "-",
        "-",
        "-",
        null,
        "DIRECT_SNMP",
        false
    );
    ResolvedMonitoringTemplate template = mock(ResolvedMonitoringTemplate.class);
    when(template.items()).thenReturn(Map.of());
    when(template.discoveryRules()).thenReturn(Map.of());

    when(monitoredDeviceRepository.findById(99L)).thenReturn(Optional.of(entity));
    when(templateResolver.resolveForDevice(eq(List.of("cisco-ios-by-snmp")), eq("Cisco"), eq("ISR"), isNull()))
        .thenReturn(template);
    when(scanService.readMonitoringDetails(eq("10.10.10.99"), eq(template))).thenReturn(emptySnmp);
    when(runtimeStateService.loadItemStateList(eq(entity)))
        .thenReturn(List.of(new ItemStateSnapshot(
            "cisco-ios-by-snmp",
            cpuItemKey,
            null,
            73.0d,
            null,
            "%",
            null,
            "ok",
            null,
            now
        )));
    when(scanService.resolveTelemetryFromItemValues(any(), eq(Map.of())))
        .thenReturn(new ItemStateTelemetrySnapshot(
            new MonitoringMetricDto(73.0d, 73.0d, 73.0d, cpuItemKey, cpuItemKey, cpuItemKey),
            61,
            null
        ));
    when(monitoringTelemetrySnapshotRepository.findByDevice_Id(99L)).thenReturn(Optional.empty());
    when(monitoringTelemetrySnapshotRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

    MonitoringServiceImpl service = monitoringServiceWithMocks(
        scanService,
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        mock(com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository.class),
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        mock(AuditLogService.class)
    );

    MonitoringDetailsDto result = service.refreshDeviceMonitoringDetailsById(99L, false);

    assertEquals(73.0d, result.cpu().current(), 1e-9);
    assertEquals(61, result.ramUsedPercent());
    verify(scanService).resolveTelemetryFromItemValues(any(), eq(Map.of()));
  }

  private static MonitoredDeviceEntity monitoredDevice(String ip, long id) {
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(id);
    entity.setIp(ip);
    entity.setHostName("host-" + id);
    entity.setName("device-" + id);
    entity.setStatus("Включено");
    entity.setHealthStatus(DeviceHealthStatus.NORM);
    entity.setAvailabilityJson("[]");
    entity.setCreatedAt(OffsetDateTime.parse("2026-04-03T12:00:00Z"));
    entity.setUpdatedAt(OffsetDateTime.parse("2026-04-03T12:00:00Z"));
    return entity;
  }

  private static MonitoringServiceImpl monitoringServiceWithMocks(
      SnmpScanService scanService,
      ConfigBackupService configBackupService,
      MetricsHistoryService metricsHistoryService,
      MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository,
      MonitoredDeviceRepository monitoredDeviceRepository,
      MonitoringEventRepository monitoringEventRepository,
      MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository,
      UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository,
      com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository priorityOverrideRepository,
      MonitoringTemplateResolver templateResolver,
      MonitoringTemplateArchiveReader templateArchiveReader,
      ZabbixRuntimeStateService runtimeStateService,
      MonitoredDeviceItemService monitoredDeviceItemService,
      AuditLogService auditLogService
  ) {
    return new MonitoringServiceImpl(
        scanService,
        new ObjectMapper(),
        configBackupService,
        metricsHistoryService,
        monitoredDeviceInterfaceRepository,
        monitoredDeviceRepository,
        monitoringEventRepository,
        monitoringTelemetrySnapshotRepository,
        uploadedMonitoringTemplateRepository,
        priorityOverrideRepository,
        templateResolver,
        templateArchiveReader,
        runtimeStateService,
        monitoredDeviceItemService,
        new UnitScalingService(),
        auditLogService
    );
  }

}
