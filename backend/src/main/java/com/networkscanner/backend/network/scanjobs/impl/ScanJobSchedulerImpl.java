package com.networkscanner.backend.network.scanjobs.impl;

import com.networkscanner.backend.network.scan.api.ScanRunService;
import com.networkscanner.backend.network.scanjobs.api.ScanJobScheduler;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobChangedEvent;
import com.networkscanner.backend.network.scanjobs.repository.ScanJobRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.boot.context.event.ApplicationReadyEvent;

@Component
public class ScanJobSchedulerImpl implements ScanJobScheduler {

  private static final Logger log = LoggerFactory.getLogger(ScanJobSchedulerImpl.class);

  private final TaskScheduler scheduler;
  private final ScanJobRepository repository;
  private final ScanRunService scanRunService;

  private final Map<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

  public ScanJobSchedulerImpl(
      TaskScheduler scanJobsTaskScheduler,
      ScanJobRepository repository,
      ScanRunService scanRunService
  ) {
    this.scheduler = scanJobsTaskScheduler;
    this.repository = repository;
    this.scanRunService = scanRunService;
  }

  @EventListener(ApplicationReadyEvent.class)
  public void onReady() {
    scheduleAllEnabled();
  }

  @EventListener(ScanJobChangedEvent.class)
  public void onJobChanged(ScanJobChangedEvent event) {
    if (event.enabled()) {
      upsert(event.jobId());
    } else {
      remove(event.jobId());
    }
  }

  @Override
  public void scheduleAllEnabled() {
    repository.findAllByEnabledTrue().forEach(job -> upsert(job.getId()));
  }

  @Override
  public void upsert(long jobId) {
    remove(jobId);

    var job = repository.findById(jobId).orElse(null);
    if (job == null || !job.isEnabled()) {
      return;
    }

    try {
      ScheduledFuture<?> future = scheduler.schedule(
          () -> {
            try {
              scanRunService.startForJob(jobId, false);
            } catch (Exception exception) {
              log.warn("Scan job failed (id={}): {}", jobId, exception.getMessage(), exception);
            }
          },
          new CronTrigger(job.getCron())
      );
      if (future != null) {
        futures.put(jobId, future);
      }
    } catch (Exception exception) {
      log.warn("Failed to schedule scan job (id={}, cron={}): {}", jobId, job.getCron(), exception.getMessage(), exception);
    }
  }

  @Override
  public void remove(long jobId) {
    ScheduledFuture<?> existing = futures.remove(jobId);
    if (existing != null) {
      existing.cancel(false);
    }
  }
}

