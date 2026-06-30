package com.networkscanner.backend.network.scanjobs.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.monitoring.api.MonitoredDeviceIpLookup;
import com.networkscanner.backend.network.scan.api.ScanRunService;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.ScanRunStartResponse;
import com.networkscanner.backend.network.scan.model.ScanRunStatus;
import com.networkscanner.backend.network.scanjobs.dto.DiscoveredNotMonitoredSummaryDto;
import com.networkscanner.backend.network.scanjobs.model.ScanJobEntity;
import com.networkscanner.backend.network.scanjobs.repository.ScanJobRepository;
import com.networkscanner.backend.network.scan.repository.ScanRunRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class ScanJobServiceImplTest {

  @Mock
  private ScanJobRepository repository;

  @Mock
  private ScanRunService scanRunService;

  @Mock
  private ScanRunRepository scanRunRepository;

  @Mock
  private ApplicationEventPublisher events;

  @Mock
  private MonitoredDeviceIpLookup monitoredDeviceIpLookup;

  @Mock
  private AuditLogService auditLogService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  private ScanJobServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new ScanJobServiceImpl(
        repository,
        objectMapper,
        scanRunService,
        scanRunRepository,
        events,
        monitoredDeviceIpLookup,
        auditLogService);
  }

  @Test
  void runNow_delegatesToScanRunService() {
    ScanRunStartResponse response = new ScanRunStartResponse(99L, 7L, ScanRunStatus.QUEUED, 254);
    when(scanRunService.startForJob(7L, true)).thenReturn(Optional.of(response));

    assertThat(service.runNow(7L)).isEqualTo(response);
    verify(scanRunService).startForJob(7L, true);
  }

  @Test
  void getDiscoveredNotMonitoredSummary_dedupesSameIpAcrossJobs() throws Exception {
    DeviceScanResult device = sampleDevice("192.168.50.10");
    String json = objectMapper.writeValueAsString(List.of(device));

    ScanJobEntity job1 = new ScanJobEntity();
    job1.setLastResultJson(json);
    ScanJobEntity job2 = new ScanJobEntity();
    job2.setLastResultJson(json);

    when(repository.findAll()).thenReturn(List.of(job1, job2));
    when(monitoredDeviceIpLookup.findMonitoredIpsIn(any())).thenReturn(Set.of());

    DiscoveredNotMonitoredSummaryDto summary = service.getDiscoveredNotMonitoredSummary();

    assertThat(summary.count()).isEqualTo(1);
  }

  @Test
  void getDiscoveredNotMonitoredSummary_excludesMonitoredIp() throws Exception {
    DeviceScanResult device = sampleDevice("10.0.0.5");
    String json = objectMapper.writeValueAsString(List.of(device));

    ScanJobEntity job = new ScanJobEntity();
    job.setLastResultJson(json);

    when(repository.findAll()).thenReturn(List.of(job));
    when(monitoredDeviceIpLookup.findMonitoredIpsIn(any())).thenReturn(Set.of("10.0.0.5"));

    assertThat(service.getDiscoveredNotMonitoredSummary().count()).isZero();
  }

  @Test
  void getDiscoveredNotMonitoredSummary_emptyWhenNoResults() {
    when(repository.findAll()).thenReturn(List.of());

    assertThat(service.getDiscoveredNotMonitoredSummary().count()).isZero();
  }

  @Test
  void getDiscoveredNotMonitoredDevices_matchesSummaryCount() throws Exception {
    DeviceScanResult device = sampleDevice("192.168.50.10");
    String json = objectMapper.writeValueAsString(List.of(device));
    ScanJobEntity job = new ScanJobEntity();
    job.setId(1L);
    job.setLastResultJson(json);

    when(repository.findAll()).thenReturn(List.of(job));
    when(monitoredDeviceIpLookup.findMonitoredIpsIn(any())).thenReturn(Set.of());

    assertThat(service.getDiscoveredNotMonitoredDevices()).hasSize(1);
    assertThat(service.getDiscoveredNotMonitoredSummary().count()).isEqualTo(1);
  }

  @Test
  void getDiscoveredNotMonitoredDevices_dedupesSameIpAcrossJobs() throws Exception {
    DeviceScanResult device = sampleDevice("10.0.0.1");
    String json = objectMapper.writeValueAsString(List.of(device));
    ScanJobEntity job1 = new ScanJobEntity();
    job1.setId(1L);
    job1.setLastResultJson(json);
    ScanJobEntity job2 = new ScanJobEntity();
    job2.setId(2L);
    job2.setLastResultJson(json);

    when(repository.findAll()).thenReturn(List.of(job1, job2));
    when(monitoredDeviceIpLookup.findMonitoredIpsIn(any())).thenReturn(Set.of());

    assertThat(service.getDiscoveredNotMonitoredDevices()).hasSize(1);
  }

  @Test
  void getDiscoveredNotMonitoredDevices_excludesMonitoredIp() throws Exception {
    DeviceScanResult device = sampleDevice("10.0.0.5");
    String json = objectMapper.writeValueAsString(List.of(device));
    ScanJobEntity job = new ScanJobEntity();
    job.setId(1L);
    job.setLastResultJson(json);

    when(repository.findAll()).thenReturn(List.of(job));
    when(monitoredDeviceIpLookup.findMonitoredIpsIn(any())).thenReturn(Set.of("10.0.0.5"));

    assertThat(service.getDiscoveredNotMonitoredDevices()).isEmpty();
  }

  private static DeviceScanResult sampleDevice(String ip) {
    return new DeviceScanResult(
        "h",
        "n",
        "sn",
        ip,
        "mac",
        "v",
        "m",
        "fw",
        "poll",
        "ok",
        "-",
        List.of()
    );
  }
}
