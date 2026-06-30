package com.networkscanner.backend.network.scan.impl;

import com.networkscanner.backend.network.scan.model.ScanRunEntity;
import com.networkscanner.backend.network.scan.model.ScanRunStatus;
import com.networkscanner.backend.network.scan.repository.ScanRunRepository;
import com.networkscanner.backend.network.scanjobs.model.ScanJobEntity;
import com.networkscanner.backend.network.scanjobs.model.ScanJobStatus;
import com.networkscanner.backend.network.scanjobs.repository.ScanJobRepository;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * После перезапуска JVM in-memory сканирование теряется, а в БД остаются {@code QUEUED}/{@code RUNNING}.
 */
@Service
public class ScanRunRecoveryService {

  static final String INTERRUPTED_MESSAGE = "Сканирование прервано перезапуском сервера.";

  private static final Logger log = LoggerFactory.getLogger(ScanRunRecoveryService.class);

  private static final List<ScanRunStatus> ACTIVE_RUN_STATUSES = List.of(
      ScanRunStatus.QUEUED,
      ScanRunStatus.RUNNING
  );

  private final ScanRunRepository scanRunRepository;
  private final ScanJobRepository scanJobRepository;

  public ScanRunRecoveryService(
      ScanRunRepository scanRunRepository,
      ScanJobRepository scanJobRepository
  ) {
    this.scanRunRepository = scanRunRepository;
    this.scanJobRepository = scanJobRepository;
  }

  @EventListener(ApplicationReadyEvent.class)
  @Order(Ordered.HIGHEST_PRECEDENCE)
  @Transactional
  public void recoverInterruptedRunsOnStartup() {
    int recoveredRuns = markInterruptedRunsFailed();
    int recoveredJobs = reconcileInterruptedJobs();
    if (recoveredRuns > 0 || recoveredJobs > 0) {
      log.info(
          "Восстановление после перезапуска: помечено прерванных запусков={}, задач автосканирования={}.",
          recoveredRuns,
          recoveredJobs
      );
    }
  }

  int markInterruptedRunsFailed() {
    List<ScanRunEntity> interruptedRuns = scanRunRepository.findByStatusIn(ACTIVE_RUN_STATUSES);
    if (interruptedRuns.isEmpty()) {
      return 0;
    }

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    for (ScanRunEntity run : interruptedRuns) {
      run.setStatus(ScanRunStatus.FAILED);
      run.setErrorMessage(INTERRUPTED_MESSAGE);
      run.setFinishedAt(now);
      run.setUpdatedAt(now);
    }
    scanRunRepository.saveAll(interruptedRuns);
    return interruptedRuns.size();
  }

  int reconcileInterruptedJobs() {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    Set<Long> updatedJobIds = new HashSet<>();
    int updatedCount = 0;

    for (ScanJobEntity job : scanJobRepository.findByLastStatus(ScanJobStatus.RUNNING)) {
      Long activeRunId = job.getActiveRunId();
      boolean shouldFail = activeRunId == null;
      if (!shouldFail) {
        ScanRunEntity activeRun = scanRunRepository.findById(activeRunId).orElse(null);
        shouldFail = activeRun == null || !ACTIVE_RUN_STATUSES.contains(activeRun.getStatus());
      }
      if (!shouldFail) {
        continue;
      }
      if (!updatedJobIds.add(job.getId())) {
        continue;
      }
      job.setLastStatus(ScanJobStatus.FAILED);
      job.setLastError(INTERRUPTED_MESSAGE);
      job.setActiveRunId(null);
      job.setUpdatedAt(now);
      scanJobRepository.save(job);
      updatedCount++;
    }

    return updatedCount;
  }
}
