package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.dto.DeviceInterfaceDto;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.MonitoringDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixDiscoveryRuleRuntime;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateCoverageReportDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateImportPreviewDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSummaryDto;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import com.networkscanner.backend.network.scan.api.ScanRunContext;
import com.networkscanner.backend.network.scan.dto.ScanExecutionResult;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MonitoringAvailabilityRefreshServiceTest {

  @Test
  @SuppressWarnings("unchecked")
  void refreshAllMarksTimedOutDevicesAsUnavailableWithoutBlockingWholeCycle() {
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    SnmpScanService scanService = new TestSnmpScanStub();
    MonitoringAvailabilityBatchWriter batchWriter = mock(MonitoringAvailabilityBatchWriter.class);
    ResolvedMonitoringTemplate template = new ResolvedMonitoringTemplate(
        "default",
        "snmp",
        "Default",
        "",
        null,
        "Cisco",
        ".*",
        1,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 1000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        new MonitoringTemplateCoverageReportDto(List.of(), List.of(), List.of()),
        true
    );
    OffsetDateTime now = OffsetDateTime.parse("2026-04-03T12:00:00Z");

    MonitoredDeviceEntity fastUp = device(1L, "10.10.10.1", now);
    MonitoredDeviceEntity slowDevice = device(2L, "10.10.10.2", now);
    MonitoredDeviceEntity fastDown = device(3L, "10.10.10.3", now);

    when(monitoredDeviceRepository.findAll()).thenReturn(List.of(fastUp, slowDevice, fastDown));

    MonitoringTemplateResolver templateResolver = new FixedTemplateResolver(template);
    MonitoringAvailabilityRefreshService service = new MonitoringAvailabilityRefreshService(
        monitoredDeviceRepository,
        templateResolver,
        scanService,
        batchWriter,
        new ObjectMapper(),
        2,
        10,
        150L,
        2
    );

    long startedAt = System.currentTimeMillis();
    try {
      service.refreshAll();
    } finally {
      service.shutdown();
    }

    ArgumentCaptor<List<MonitoringAvailabilityRefreshResult>> captor = ArgumentCaptor.forClass(List.class);
    verify(batchWriter, atLeastOnce()).writeBatch(captor.capture());

    List<MonitoringAvailabilityRefreshResult> persisted = new ArrayList<>();
    for (List<MonitoringAvailabilityRefreshResult> batch : captor.getAllValues()) {
      persisted.addAll(batch);
    }

    assertEquals(3, persisted.size());
    assertTrue(persisted.stream().anyMatch(result ->
        "10.10.10.1".equals(result.deviceIp()) && "Включено".equals(result.status())));
    assertTrue(persisted.stream().anyMatch(result ->
        "10.10.10.2".equals(result.deviceIp()) && "Недоступно".equals(result.status())));
    assertTrue(persisted.stream().anyMatch(result ->
        "10.10.10.3".equals(result.deviceIp()) && "Недоступно".equals(result.status())));
    assertTrue(
        System.currentTimeMillis() - startedAt < 3_000L,
        () -> "Цикл не должен ждать завершения «медленного» ICMP целиком; durationMs=" + (System.currentTimeMillis() - startedAt)
    );
  }

  /** Всегда один и тот же шаблон; без Mockito — потокобезопасно при параллельном {@code resolveForDevice}. */
  private static final class FixedTemplateResolver implements MonitoringTemplateResolver {

    private final ResolvedMonitoringTemplate template;

    private FixedTemplateResolver(ResolvedMonitoringTemplate template) {
      this.template = template;
    }

    @Override
    public void initialize() {
      // no-op
    }

    @Override
    public List<MonitoringTemplateSummaryDto> listTemplates() {
      return List.of();
    }

    @Override
    public MonitoringTemplateDetailsDto describeTemplate(String templateId) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MonitoringTemplateImportPreviewDto previewArchive(String originalFilename, byte[] archiveBytes) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ResolvedMonitoringTemplate resolveTemplateById(String templateId) {
      return template;
    }

    @Override
    public ResolvedMonitoringTemplate resolveForDevice(String selectedTemplateId, String vendor, String model) {
      return template;
    }

    @Override
    public ResolvedMonitoringTemplate resolveForDevice(
        String selectedTemplateId,
        String vendor,
        String model,
        String firmwareVersion
    ) {
      return template;
    }

    @Override
    public ResolvedMonitoringTemplate resolveForDevice(List<String> selectedTemplateIds, String vendor, String model) {
      return template;
    }

    @Override
    public ResolvedMonitoringTemplate resolveForDevice(
        List<String> selectedTemplateIds,
        String vendor,
        String model,
        String firmwareVersion
    ) {
      return template;
    }

    @Override
    public ResolvedMonitoringTemplate resolveMergedTemplates(List<String> templateIds) {
      return template;
    }

    @Override
    public String mapValue(String templateId, String valueMapName, String rawValue) {
      return rawValue;
    }
  }

  /**
   * Mockito-мок {@link SnmpScanService} не рассчитан на параллельные вызовы из пула CompletionService.
   * Простая заглушка без общего {@code synchronized} на весь объект: иначе «медленный» ICMP держит монитор
   * и блокирует быстрые устройства, что ломает смысл теста (параллельный цикл).
   */
  private static final class TestSnmpScanStub implements SnmpScanService {

    @Override
    public boolean checkIcmpReachable(String ip, int timeout) {
      if ("10.10.10.1".equals(ip)) {
        return true;
      }
      if ("10.10.10.2".equals(ip)) {
        try {
          Thread.sleep(500);
        } catch (InterruptedException exception) {
          Thread.currentThread().interrupt();
        }
        return false;
      }
      if ("10.10.10.3".equals(ip)) {
        return false;
      }
      return false;
    }

    @Override
    public boolean checkPortReachable(String ip, int port, int timeout) {
      if (port != 22) {
        return false;
      }
      return "10.10.10.1".equals(ip);
    }

    @Override
    public boolean checkSnmpReachable(String ip, ResolvedMonitoringTemplate template) {
      return "10.10.10.1".equals(ip);
    }

    @Override
    public ScanExecutionResult scan(ScanRequest request, ScanRunContext context) {
      throw new UnsupportedOperationException();
    }

    @Override
    public boolean stopScan(long runId) {
      return false;
    }

    @Override
    public boolean checkSnmpReachable(String ip, int port, int timeout, int retries, String community) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<DeviceInterfaceDto> readInterfaces(String ip, int port, int timeout, int retries, String community) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<DeviceInterfaceDto> readInterfaces(String ip, ResolvedMonitoringTemplate template) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MonitoringDetailsDto readMonitoringDetails(String ip, int port, int timeout, int retries, String community) {
      throw new UnsupportedOperationException();
    }

    @Override
    public MonitoringDetailsDto readMonitoringDetails(String ip, ResolvedMonitoringTemplate template) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, Double> readMonitoringMetrics(String ip, ResolvedMonitoringTemplate template) {
      throw new UnsupportedOperationException();
    }

    @Override
    public com.networkscanner.backend.monitoring.dto.ItemStateTelemetrySnapshot resolveTelemetryFromItemValues(
        Map<String, Double> itemValues,
        Map<String, com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime> itemDefinitions
    ) {
      throw new UnsupportedOperationException();
    }

    @Override
    public Map<String, String> readRawOids(String ip, ResolvedMonitoringTemplate template, Map<String, String> requestedOids) {
      throw new UnsupportedOperationException();
    }

    @Override
    public List<DiscoveryInstanceRuntime> executeDiscovery(
        String ip,
        ResolvedMonitoringTemplate template,
        ZabbixDiscoveryRuleRuntime discoveryRule,
        OffsetDateTime timestamp
    ) {
      return Collections.emptyList();
    }
  }

  private MonitoredDeviceEntity device(Long id, String ip, OffsetDateTime updatedAt) {
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(id);
    entity.setIp(ip);
    entity.setVendor("Cisco");
    entity.setModel("ISR");
    entity.setTemplateId("default");
    entity.setTemplateIds("default");
    entity.setStatus("Включено");
    entity.setAvailabilityJson("[]");
    entity.setUpdatedAt(updatedAt);
    return entity;
  }
}
