package com.networkscanner.backend.network.scan.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.network.scan.model.ScanRunEntity;
import com.networkscanner.backend.network.scan.model.ScanRunSource;
import com.networkscanner.backend.network.scan.model.ScanRunStatus;
import com.networkscanner.backend.network.scan.repository.ScanRunRepository;
import com.networkscanner.backend.network.scanjobs.model.ScanJobEntity;
import com.networkscanner.backend.network.scanjobs.model.ScanJobStatus;
import com.networkscanner.backend.network.scanjobs.repository.ScanJobRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ScanRunRecoveryServiceTest {

  @Mock
  private ScanRunRepository scanRunRepository;

  @Mock
  private ScanJobRepository scanJobRepository;

  private ScanRunRecoveryService service;

  @BeforeEach
  void setUp() {
    service = new ScanRunRecoveryService(scanRunRepository, scanJobRepository);
  }

  @Test
  void recoverInterruptedRunsOnStartup_marksRunsAndJobsFailed() {
    ScanRunEntity run = new ScanRunEntity();
    run.setId(10L);
    run.setSource(ScanRunSource.JOB);
    run.setScanJobId(90L);
    run.setStatus(ScanRunStatus.RUNNING);
    run.setScannedAddresses(51);
    run.setTotalAddresses(254);

    ScanJobEntity job = new ScanJobEntity();
    job.setId(90L);
    job.setName("job-90");
    job.setLastStatus(ScanJobStatus.RUNNING);
    job.setActiveRunId(10L);

    when(scanRunRepository.findByStatusIn(any())).thenReturn(List.of(run));
    when(scanJobRepository.findByLastStatus(ScanJobStatus.RUNNING)).thenReturn(List.of(job));
    when(scanRunRepository.findById(10L)).thenReturn(Optional.of(run));

    service.recoverInterruptedRunsOnStartup();

    assertThat(run.getStatus()).isEqualTo(ScanRunStatus.FAILED);
    assertThat(run.getErrorMessage()).isEqualTo(ScanRunRecoveryService.INTERRUPTED_MESSAGE);
    assertThat(run.getFinishedAt()).isNotNull();

    ArgumentCaptor<ScanJobEntity> jobCaptor = ArgumentCaptor.forClass(ScanJobEntity.class);
    verify(scanJobRepository).save(jobCaptor.capture());
    ScanJobEntity savedJob = jobCaptor.getValue();
    assertThat(savedJob.getLastStatus()).isEqualTo(ScanJobStatus.FAILED);
    assertThat(savedJob.getLastError()).isEqualTo(ScanRunRecoveryService.INTERRUPTED_MESSAGE);
    assertThat(savedJob.getActiveRunId()).isNull();
  }
}
