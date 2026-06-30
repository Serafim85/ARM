package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.dto.AvailabilityDto;
import com.networkscanner.backend.monitoring.util.MonitoringSnmpTemplateSupport;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorCompletionService;
import com.networkscanner.backend.util.concurrent.NamedExecutors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * ICMP/SNMP/SSH reachability refresh; worker threads use the {@code monitoring-availability-N} prefix
 * (see {@link com.networkscanner.backend.util.concurrent.NamedExecutors}).
 */
@Service
@ConditionalOnProperty(name = "monitoring.availability-refresh.enabled", havingValue = "true", matchIfMissing = true)
public class MonitoringAvailabilityRefreshService {

  private static final Logger log = LoggerFactory.getLogger(MonitoringAvailabilityRefreshService.class);

  private static final String STATUS_UP = "Включено";
  private static final String STATUS_DOWN = "Недоступно";
  private static final int SSH_PORT = 22;
  private static final long COMPLETION_POLL_MS = 200L;

  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final MonitoringTemplateResolver templateResolver;
  private final SnmpScanService scanService;
  private final MonitoringAvailabilityBatchWriter batchWriter;
  private final ObjectMapper objectMapper;
  private final ExecutorService executor;
  private final int networkTimeoutMs;
  private final long perDeviceTimeoutMs;
  private final int batchSize;

  public MonitoringAvailabilityRefreshService(
      MonitoredDeviceRepository monitoredDeviceRepository,
      MonitoringTemplateResolver templateResolver,
      SnmpScanService scanService,
      MonitoringAvailabilityBatchWriter batchWriter,
      ObjectMapper objectMapper,
      @Value("${monitoring.availability-refresh.threads:16}") int threads,
      @Value("${monitoring.availability-refresh.network-timeout-ms:2000}") int networkTimeoutMs,
      @Value("${monitoring.availability-refresh.per-device-timeout-ms:8000}") long perDeviceTimeoutMs,
      @Value("${monitoring.availability-refresh.batch-size:100}") int batchSize
  ) {
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.templateResolver = templateResolver;
    this.scanService = scanService;
    this.batchWriter = batchWriter;
    this.objectMapper = objectMapper;
    this.executor = NamedExecutors.newFixedThreadPool(Math.max(threads, 1), "monitoring-availability-");
    this.networkTimeoutMs = Math.max(networkTimeoutMs, 1);
    this.perDeviceTimeoutMs = Math.max(perDeviceTimeoutMs, 1L);
    this.batchSize = Math.max(batchSize, 1);
  }

  @Scheduled(fixedDelayString = "${monitoring.availability-refresh.interval-ms:60000}")
  public void refreshAll() {
    List<MonitoredDeviceEntity> devices = monitoredDeviceRepository.findAll();
    if (devices.isEmpty()) {
      return;
    }

    long startedAt = System.currentTimeMillis();
    OffsetDateTime recordedAt = OffsetDateTime.now();
    CompletionService<MonitoringAvailabilityRefreshResult> completionService =
        new ExecutorCompletionService<>(executor);
    Map<Future<MonitoringAvailabilityRefreshResult>, PendingRefreshTask> pending = new LinkedHashMap<>();
    List<MonitoringAvailabilityRefreshResult> batch = new ArrayList<>(Math.min(devices.size(), batchSize));
    int successCount = 0;
    int timeoutCount = 0;
    int failureCount = 0;

    for (MonitoredDeviceEntity device : devices) {
      Future<MonitoringAvailabilityRefreshResult> future =
          completionService.submit(() -> probeDevice(device, recordedAt));
      pending.put(future, new PendingRefreshTask(device, System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(perDeviceTimeoutMs)));
    }

    try {
      while (!pending.isEmpty()) {
        Future<MonitoringAvailabilityRefreshResult> completed = completionService.poll(
            nextPollWaitMs(pending),
            TimeUnit.MILLISECONDS
        );
        if (completed != null) {
          PendingRefreshTask task = pending.remove(completed);
          if (task != null) {
            try {
              batch.add(completed.get());
              successCount++;
            } catch (CancellationException exception) {
              timeoutCount++;
              log.warn("Обновление доступности отменено по timeout для устройства {}", task.device().getIp());
              batch.add(timeoutResult(task.device(), recordedAt));
            } catch (ExecutionException exception) {
              failureCount++;
              log.warn(
                  "Ошибка обновления доступности устройства {}: {}",
                  task.device().getIp(),
                  exception.getCause() == null ? exception.getMessage() : exception.getCause().getMessage()
              );
              batch.add(failureResult(task.device(), recordedAt));
            }
            flushBatch(batch);
          }
        }

        long now = System.nanoTime();
        Iterator<Map.Entry<Future<MonitoringAvailabilityRefreshResult>, PendingRefreshTask>> iterator =
            pending.entrySet().iterator();
        while (iterator.hasNext()) {
          Map.Entry<Future<MonitoringAvailabilityRefreshResult>, PendingRefreshTask> entry = iterator.next();
          if (entry.getValue().deadlineNanos() > now) {
            continue;
          }
          entry.getKey().cancel(true);
          timeoutCount++;
          log.warn("Превышен timeout обновления доступности для устройства {}", entry.getValue().device().getIp());
          batch.add(timeoutResult(entry.getValue().device(), recordedAt));
          iterator.remove();
          flushBatch(batch);
        }
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      log.warn("Фоновое обновление доступности прервано.");
      for (Future<MonitoringAvailabilityRefreshResult> future : pending.keySet()) {
        future.cancel(true);
      }
    }

    flushRemaining(batch);
    log.info(
        "Цикл обновления доступности завершён: devices={}, success={}, timeout={}, failed={}, durationMs={}",
        devices.size(),
        successCount,
        timeoutCount,
        failureCount,
        System.currentTimeMillis() - startedAt
    );
  }

  @PreDestroy
  public void shutdown() {
    executor.shutdown();
    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        executor.shutdownNow();
      }
    } catch (InterruptedException exception) {
      executor.shutdownNow();
      Thread.currentThread().interrupt();
    }
  }

  private MonitoringAvailabilityRefreshResult probeDevice(MonitoredDeviceEntity device, OffsetDateTime recordedAt) {
    try {
      ResolvedMonitoringTemplate template = templateResolver.resolveForDevice(
          MonitoringTemplateSelectionSupport.parseStored(device.getTemplateIds(), device.getTemplateId()),
          device.getVendor(),
          device.getModel(),
          device.getFirmwareVersion()
      );
      template = applyDeviceSnmpOverrides(template, device);
      boolean icmpReachable = scanService.checkIcmpReachable(device.getIp(), networkTimeoutMs);
      boolean sshReachable = scanService.checkPortReachable(device.getIp(), SSH_PORT, networkTimeoutMs);
      boolean snmpReachable = scanService.checkSnmpReachable(device.getIp(), template);
      return buildResult(device, recordedAt, icmpReachable, snmpReachable, sshReachable);
    } catch (Exception exception) {
      log.warn(
          "Устройство {} считается недоступным из-за ошибки проверки: {}",
          device.getIp(),
          exception.getMessage()
      );
      return failureResult(device, recordedAt);
    }
  }

  private MonitoringAvailabilityRefreshResult timeoutResult(MonitoredDeviceEntity device, OffsetDateTime recordedAt) {
    return buildResult(device, recordedAt, false, false, false);
  }

  private MonitoringAvailabilityRefreshResult failureResult(MonitoredDeviceEntity device, OffsetDateTime recordedAt) {
    return buildResult(device, recordedAt, false, false, false);
  }

  private MonitoringAvailabilityRefreshResult buildResult(
      MonitoredDeviceEntity device,
      OffsetDateTime recordedAt,
      boolean icmpReachable,
      boolean snmpReachable,
      boolean sshReachable
  ) {
    String status = icmpReachable || snmpReachable || sshReachable ? STATUS_UP : STATUS_DOWN;
    String availabilityJson = writeAvailability(List.of(
        new AvailabilityDto("ICMP", icmpReachable, icmpReachable ? "green" : "red"),
        new AvailabilityDto("SNMP", snmpReachable, snmpReachable ? "green" : "red"),
        new AvailabilityDto("SSH", sshReachable, sshReachable ? "green" : "red")
    ));
    return new MonitoringAvailabilityRefreshResult(
        device.getId(),
        device.getIp(),
        status,
        device.getStatus(),
        icmpReachable,
        snmpReachable,
        sshReachable,
        availabilityJson,
        device.getAvailabilityJson(),
        device.getUpdatedAt(),
        recordedAt
    );
  }

  private String writeAvailability(List<AvailabilityDto> availability) {
    try {
      return objectMapper.writeValueAsString(availability);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Не удалось сериализовать доступность устройства.", exception);
    }
  }

  private void flushBatch(List<MonitoringAvailabilityRefreshResult> batch) {
    if (batch.size() >= batchSize) {
      flushRemaining(batch);
    }
  }

  private long nextPollWaitMs(Map<Future<MonitoringAvailabilityRefreshResult>, PendingRefreshTask> pending) {
    long now = System.nanoTime();
    long nearestDeadlineNanos = pending.values().stream()
        .mapToLong(PendingRefreshTask::deadlineNanos)
        .min()
        .orElse(now + TimeUnit.MILLISECONDS.toNanos(COMPLETION_POLL_MS));
    long waitNanos = Math.max(nearestDeadlineNanos - now, 0L);
    return Math.max(1L, Math.min(TimeUnit.NANOSECONDS.toMillis(waitNanos), COMPLETION_POLL_MS));
  }

  private void flushRemaining(List<MonitoringAvailabilityRefreshResult> batch) {
    if (batch.isEmpty()) {
      return;
    }
    batchWriter.writeBatch(List.copyOf(batch));
    batch.clear();
  }

  private record PendingRefreshTask(MonitoredDeviceEntity device, long deadlineNanos) {
  }

  private ResolvedMonitoringTemplate applyDeviceSnmpOverrides(
      ResolvedMonitoringTemplate template,
      MonitoredDeviceEntity device
  ) {
    return MonitoringSnmpTemplateSupport.applyDeviceSnmpOverrides(template, device);
  }
}
