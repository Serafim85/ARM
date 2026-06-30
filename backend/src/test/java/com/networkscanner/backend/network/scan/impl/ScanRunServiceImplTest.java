package com.networkscanner.backend.network.scan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.accessprofiles.api.AccessProfileResolver;
import com.networkscanner.backend.monitoring.api.MonitoredDeviceIpLookup;
import com.networkscanner.backend.monitoring.api.MonitoringService;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import com.networkscanner.backend.network.scan.dto.ScanRunStartResponse;
import com.networkscanner.backend.network.scan.model.ScanRunEntity;
import com.networkscanner.backend.network.scan.model.ScanRunSource;
import com.networkscanner.backend.network.scan.model.ScanRunStatus;
import com.networkscanner.backend.network.scan.repository.ScanRunRepository;
import com.networkscanner.backend.network.scan.util.IpRangeParser;
import com.networkscanner.backend.network.scanjobs.repository.ScanJobRepository;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ScanRunServiceImplTest {

  @Mock
  private ScanRunRepository scanRunRepository;

  @Mock
  private ScanJobRepository scanJobRepository;

  @Mock
  private com.networkscanner.backend.network.scan.api.SnmpScanService snmpScanService;

  @Mock
  private AccessProfileResolver accessProfileResolver;

  @Mock
  private MonitoredDeviceIpLookup monitoredDeviceIpLookup;

  @Mock
  private MonitoringService monitoringService;

  @Mock
  private NotificationDispatchService notificationDispatchService;

  @Mock
  private TransactionTemplate transactionTemplate;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final IpRangeParser ipRangeParser = new IpRangeParser();

  private ScanRunServiceImpl service;

  @BeforeEach
  void setUp() {
    ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
    executor.setCorePoolSize(1);
    executor.setMaxPoolSize(1);
    executor.setQueueCapacity(4);
    executor.initialize();

    service = new ScanRunServiceImpl(
        scanRunRepository,
        scanJobRepository,
        objectMapper,
        snmpScanService,
        ipRangeParser,
        accessProfileResolver,
        monitoredDeviceIpLookup,
        monitoringService,
        notificationDispatchService,
        executor,
        executor,
        transactionTemplate);
  }

  @Test
  void startManual_persistsQueuedRunAndSubmitsExecution() throws Exception {
    ScanRequest request = new ScanRequest(
        "192.168.1.1-2",
        null,
        null,
        161,
        1000,
        0,
        "public",
        null,
        null,
        null,
        null,
        null,
        null,
        List.of()
    );
    when(accessProfileResolver.resolveScanRequest(request)).thenReturn(request);

    ScanRunEntity saved = new ScanRunEntity();
    saved.setId(5L);
    saved.setSource(ScanRunSource.MANUAL);
    saved.setStatus(ScanRunStatus.QUEUED);
    saved.setTotalAddresses(2);
    saved.setRequestJson(objectMapper.writeValueAsString(request));
    saved.setCreatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    saved.setUpdatedAt(saved.getCreatedAt());

    when(scanRunRepository.save(any(ScanRunEntity.class))).thenReturn(saved);

    ScanRunStartResponse response = service.startManual(request);

    assertThat(response.runId()).isEqualTo(5L);
    assertThat(response.totalAddresses()).isEqualTo(2);
    ArgumentCaptor<ScanRunEntity> captor = ArgumentCaptor.forClass(ScanRunEntity.class);
    verify(scanRunRepository).save(captor.capture());
    assertThat(captor.getValue().getSource()).isEqualTo(ScanRunSource.MANUAL);
  }

  @Test
  void getResults_rejectsNonSuccessStatus() {
    ScanRunEntity entity = new ScanRunEntity();
    entity.setId(1L);
    entity.setStatus(ScanRunStatus.RUNNING);
    when(scanRunRepository.findById(1L)).thenReturn(Optional.of(entity));

    assertThatThrownBy(() -> service.getResults(1L))
        .isInstanceOf(ResponseStatusException.class)
        .hasMessageContaining("Результаты доступны только после успешного завершения");
  }
}
