package com.networkscanner.backend.network.scan.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.accessprofiles.api.AccessProfileResolver;
import com.networkscanner.backend.config.ScanRunExecutorConfig;
import com.networkscanner.backend.monitoring.api.MonitoredDeviceIpLookup;
import com.networkscanner.backend.monitoring.api.MonitoringService;
import com.networkscanner.backend.monitoring.dto.MonitoringSnmpCredentials;
import com.networkscanner.backend.network.scan.api.ScanRunContext;
import com.networkscanner.backend.network.scan.api.ScanRunService;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.DiscoveryProbeConfig;
import com.networkscanner.backend.network.scan.dto.ScanExecutionResult;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import com.networkscanner.backend.network.scan.dto.ScanRunDto;
import com.networkscanner.backend.network.scan.dto.ScanRunStartResponse;
import com.networkscanner.backend.network.scan.model.ScanRunEntity;
import com.networkscanner.backend.network.scan.model.ScanRunSource;
import com.networkscanner.backend.network.scan.model.ScanRunStatus;
import com.networkscanner.backend.network.scan.repository.ScanRunRepository;
import com.networkscanner.backend.network.scan.util.IpRangeParser;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobRequest;
import com.networkscanner.backend.network.scanjobs.model.ScanJobEntity;
import com.networkscanner.backend.network.scanjobs.model.ScanJobStatus;
import com.networkscanner.backend.network.scanjobs.repository.ScanJobRepository;
import com.networkscanner.backend.notifications.api.NotificationDispatchService;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ScanRunServiceImpl implements ScanRunService {

  private static final Logger log = LoggerFactory.getLogger(ScanRunServiceImpl.class);

  private static final TypeReference<List<DeviceScanResult>> DEVICE_SCAN_RESULT_LIST =
      new TypeReference<>() {};

  private static final List<ScanRunStatus> ACTIVE_STATUSES = List.of(
      ScanRunStatus.QUEUED,
      ScanRunStatus.RUNNING
  );

  private final ScanRunRepository scanRunRepository;
  private final ScanJobRepository scanJobRepository;
  private final ObjectMapper objectMapper;
  private final SnmpScanService snmpScanService;
  private final IpRangeParser ipRangeParser;
  private final AccessProfileResolver accessProfileResolver;
  private final MonitoredDeviceIpLookup monitoredDeviceIpLookup;
  private final MonitoringService monitoringService;
  private final NotificationDispatchService notificationDispatchService;
  private final ThreadPoolTaskExecutor manualScanRunTaskExecutor;
  private final ThreadPoolTaskExecutor jobScanRunTaskExecutor;
  private final TransactionTemplate transactionTemplate;

  public ScanRunServiceImpl(
      ScanRunRepository scanRunRepository,
      ScanJobRepository scanJobRepository,
      ObjectMapper objectMapper,
      SnmpScanService snmpScanService,
      IpRangeParser ipRangeParser,
      AccessProfileResolver accessProfileResolver,
      MonitoredDeviceIpLookup monitoredDeviceIpLookup,
      MonitoringService monitoringService,
      NotificationDispatchService notificationDispatchService,
      @Qualifier(ScanRunExecutorConfig.MANUAL_SCAN_RUN_TASK_EXECUTOR)
          ThreadPoolTaskExecutor manualScanRunTaskExecutor,
      @Qualifier(ScanRunExecutorConfig.JOB_SCAN_RUN_TASK_EXECUTOR)
          ThreadPoolTaskExecutor jobScanRunTaskExecutor,
      TransactionTemplate transactionTemplate
  ) {
    this.scanRunRepository = scanRunRepository;
    this.scanJobRepository = scanJobRepository;
    this.objectMapper = objectMapper;
    this.snmpScanService = snmpScanService;
    this.ipRangeParser = ipRangeParser;
    this.accessProfileResolver = accessProfileResolver;
    this.monitoredDeviceIpLookup = monitoredDeviceIpLookup;
    this.monitoringService = monitoringService;
    this.notificationDispatchService = notificationDispatchService;
    this.manualScanRunTaskExecutor = manualScanRunTaskExecutor;
    this.jobScanRunTaskExecutor = jobScanRunTaskExecutor;
    this.transactionTemplate = transactionTemplate;
  }

  @Override
  @Transactional
  public ScanRunStartResponse startManual(ScanRequest request) {
    ScanRequest effectiveRequest = accessProfileResolver.resolveScanRequest(request);
    int totalAddresses = ipRangeParser.expandRange(effectiveRequest.subnetRange()).size();
    ScanRunEntity entity = createRunEntity(
        ScanRunSource.MANUAL,
        null,
        writeScanRequestJson(effectiveRequest),
        totalAddresses
    );
    entity = scanRunRepository.save(entity);
    submitRunAfterCommit(entity.getId(), ScanRunSource.MANUAL);
    return toStartResponse(entity);
  }

  @Override
  @Transactional
  public Optional<ScanRunStartResponse> startForJob(long jobId, boolean failIfRunning) {
    ScanJobEntity job = getJobEntity(jobId);
    if (hasActiveRun(jobId)) {
      if (failIfRunning) {
        throw new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Сканирование уже выполняется."
        );
      }
      log.info("Scan job id={} skipped: previous run is still active.", jobId);
      return Optional.empty();
    }

    ScanJobRequest jobRequest = readJobRequestJson(job.getRequestJson());
    ScanRequest scanRequest = accessProfileResolver.resolveScanRequest(jobRequest.scan());
    int totalAddresses = ipRangeParser.expandRange(scanRequest.subnetRange()).size();

    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ScanRunEntity entity = createRunEntity(
        ScanRunSource.JOB,
        jobId,
        writeScanRequestJson(scanRequest),
        totalAddresses
    );
    entity = scanRunRepository.save(entity);

    job.setLastStatus(ScanJobStatus.RUNNING);
    job.setLastError(null);
    job.setActiveRunId(entity.getId());
    job.setUpdatedAt(now);
    scanJobRepository.save(job);

    notificationDispatchService.notifyScanJobEvent(
        job.getId(),
        job.getName(),
        "SCAN_JOB_SCHEDULED",
        "Запущено плановое сканирование."
    );

    submitRunAfterCommit(entity.getId(), ScanRunSource.JOB);
    return Optional.of(toStartResponse(entity));
  }

  @Override
  @Transactional(readOnly = true)
  public ScanRunDto getStatus(long runId) {
    return toDto(getRunEntity(runId));
  }

  @Override
  @Transactional(readOnly = true)
  public List<DeviceScanResult> getResults(long runId) {
    ScanRunEntity entity = getRunEntity(runId);
    if (entity.getStatus() != ScanRunStatus.SUCCESS) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Результаты доступны только после успешного завершения сканирования."
      );
    }
    return readResultJson(entity.getResultJson());
  }

  @Override
  public boolean stop(long runId) {
    ScanRunEntity entity = getRunEntity(runId);
    if (!ACTIVE_STATUSES.contains(entity.getStatus())) {
      return false;
    }
    if (entity.getStatus() == ScanRunStatus.QUEUED) {
      markCancelled(runId);
      return true;
    }
    snmpScanService.stopScan(runId);
    markCancelled(runId);
    return true;
  }

  private void submitRunAfterCommit(long runId, ScanRunSource source) {
    ThreadPoolTaskExecutor scanRunTaskExecutor = scanRunExecutorFor(source);
    Runnable task = () -> {
      try {
        executeRun(runId);
      } catch (RuntimeException exception) {
        log.error("Scan run failed to start (id={}): {}", runId, exception.getMessage(), exception);
      }
    };
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
        @Override
        public void afterCommit() {
          scanRunTaskExecutor.execute(task);
        }
      });
      return;
    }
    scanRunTaskExecutor.execute(task);
  }

  private ThreadPoolTaskExecutor scanRunExecutorFor(ScanRunSource source) {
    return source == ScanRunSource.JOB ? jobScanRunTaskExecutor : manualScanRunTaskExecutor;
  }

  private void executeRun(long runId) {
    ScanRunEntity run = getRunEntity(runId);
    if (run.getStatus() == ScanRunStatus.CANCELLED) {
      return;
    }
    if (run.getStatus() != ScanRunStatus.QUEUED) {
      return;
    }

    OffsetDateTime startedAt = OffsetDateTime.now(ZoneOffset.UTC);
    transactionTemplate.executeWithoutResult(status -> {
      ScanRunEntity entity = getRunEntity(runId);
      entity.setStatus(ScanRunStatus.RUNNING);
      entity.setStartedAt(startedAt);
      entity.setUpdatedAt(startedAt);
      scanRunRepository.save(entity);
    });

    if (getRunEntity(runId).getStatus() == ScanRunStatus.CANCELLED) {
      return;
    }

    ScanRequest scanRequest = readScanRequestJson(run.getRequestJson());
    AtomicBoolean stopRequested = new AtomicBoolean(false);
    ScanRunContext context = new ScanRunContext(
        runId,
        run.getSource(),
        stopRequested,
        (scanned, total) -> updateProgress(runId, scanned, total)
    );

    try {
      ScanExecutionResult execution = snmpScanService.scan(scanRequest, context);
      List<DeviceScanResult> results = execution.results() != null ? execution.results() : List.of();
      if (execution.cancelled()) {
        markCancelled(runId);
        return;
      }
      completeSuccess(runId, results);
    } catch (RuntimeException exception) {
      log.warn("Scan run failed (id={}): {}", runId, exception.getMessage(), exception);
      markFailed(runId, exception.getMessage());
    }
  }

  private void updateProgress(long runId, int scannedAddresses, int totalAddresses) {
    transactionTemplate.executeWithoutResult(status -> {
      ScanRunEntity entity = scanRunRepository.findById(runId).orElse(null);
      if (entity == null || !ACTIVE_STATUSES.contains(entity.getStatus())) {
        return;
      }
      entity.setScannedAddresses(scannedAddresses);
      entity.setTotalAddresses(totalAddresses);
      entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
      scanRunRepository.save(entity);
    });
  }

  private void completeSuccess(long runId, List<DeviceScanResult> results) {
    transactionTemplate.executeWithoutResult(status -> {
      ScanRunEntity entity = getRunEntity(runId);
      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      entity.setStatus(ScanRunStatus.SUCCESS);
      entity.setScannedAddresses(entity.getTotalAddresses());
      entity.setFoundCount(results.size());
      entity.setResultJson(writeResultJson(results));
      entity.setFinishedAt(now);
      entity.setUpdatedAt(now);
      scanRunRepository.save(entity);

      if (entity.getSource() == ScanRunSource.JOB && entity.getScanJobId() != null) {
        finalizeJobSuccess(entity.getScanJobId(), entity.getId(), results, now);
      }
    });
  }

  private void finalizeJobSuccess(
      long jobId,
      long runId,
      List<DeviceScanResult> results,
      OffsetDateTime startedAt
  ) {
    ScanJobEntity job = getJobEntity(jobId);
    ScanJobRequest jobRequest = readJobRequestJson(job.getRequestJson());

    if (jobRequest.autoMonitoringEnabled() && results != null && !results.isEmpty()) {
      try {
        Set<String> ips = results.stream()
            .filter(r -> r != null && r.ip() != null && !r.ip().isBlank())
            .map(r -> r.ip().trim())
            .filter(ip -> !ip.isEmpty())
            .collect(java.util.stream.Collectors.toSet());
        if (!ips.isEmpty()) {
          Set<String> alreadyMonitored = monitoredDeviceIpLookup.findMonitoredIpsIn(ips);
          List<DeviceScanResult> toActivate = results.stream()
              .filter(r -> r != null && r.ip() != null && !r.ip().isBlank())
              .filter(r -> !alreadyMonitored.contains(r.ip().trim()))
              .toList();
          if (!toActivate.isEmpty()) {
            List<String> templateIds = jobRequest.normalizedMonitoringTemplateIds();
            monitoringService.activate(
                toActivate,
                templateIds.isEmpty() ? null : templateIds.get(0),
                templateIds,
                Map.of(),
                Map.of(),
                snmpCredentialsForScan(jobRequest.scan()),
                null
            );
          }
        }
      } catch (RuntimeException monitoringException) {
        String message = monitoringException.getMessage();
        log.warn(
            "Автопостановка на мониторинг для scan job id={} завершилась ошибкой: {}",
            job.getId(),
            message,
            monitoringException
        );
        job.setLastError(message != null && !message.isBlank()
            ? "Автопостановка на мониторинг: " + message
            : "Автопостановка на мониторинг завершилась ошибкой.");
      }
    }

    job.setLastRunAt(startedAt);
    job.setLastStatus(ScanJobStatus.SUCCESS);
    job.setLastResultCount(results.size());
    job.setLastResultJson(writeResultJson(results));
    job.setActiveRunId(null);
    job.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    scanJobRepository.save(job);

    List<DeviceScanResult> discoveredNotMonitored = computeDiscoveredNotMonitoredDevices();
    if (!discoveredNotMonitored.isEmpty()) {
      notificationDispatchService.notifyNewDevicesDiscovered(job.getId(), job.getName(), discoveredNotMonitored);
    }
    notificationDispatchService.notifyScanJobEvent(
        job.getId(),
        job.getName(),
        "SCAN_JOB_COMPLETED",
        "Сканирование завершено. Найдено устройств: " + results.size()
    );
  }

  private void markFailed(long runId, String errorMessage) {
    transactionTemplate.executeWithoutResult(status -> {
      ScanRunEntity entity = getRunEntity(runId);
      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      entity.setStatus(ScanRunStatus.FAILED);
      entity.setErrorMessage(errorMessage);
      entity.setFinishedAt(now);
      entity.setUpdatedAt(now);
      scanRunRepository.save(entity);

      if (entity.getSource() == ScanRunSource.JOB && entity.getScanJobId() != null) {
        ScanJobEntity job = getJobEntity(entity.getScanJobId());
        job.setLastRunAt(entity.getStartedAt() != null ? entity.getStartedAt() : now);
        job.setLastStatus(ScanJobStatus.FAILED);
        job.setLastError(errorMessage);
        job.setActiveRunId(null);
        job.setUpdatedAt(now);
        scanJobRepository.save(job);
        notificationDispatchService.notifyScanJobEvent(
            job.getId(),
            job.getName(),
            "SCAN_JOB_FAILED",
            errorMessage
        );
      }
    });
  }

  private void markCancelled(long runId) {
    transactionTemplate.executeWithoutResult(status -> {
      ScanRunEntity entity = getRunEntity(runId);
      if (entity.getStatus() == ScanRunStatus.SUCCESS
          || entity.getStatus() == ScanRunStatus.FAILED
          || entity.getStatus() == ScanRunStatus.CANCELLED) {
        return;
      }
      OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
      entity.setStatus(ScanRunStatus.CANCELLED);
      entity.setErrorMessage("Сканирование остановлено.");
      entity.setFinishedAt(now);
      entity.setUpdatedAt(now);
      scanRunRepository.save(entity);

      if (entity.getSource() == ScanRunSource.JOB && entity.getScanJobId() != null) {
        ScanJobEntity job = getJobEntity(entity.getScanJobId());
        job.setLastStatus(ScanJobStatus.FAILED);
        job.setLastError("Сканирование остановлено.");
        job.setActiveRunId(null);
        job.setUpdatedAt(now);
        scanJobRepository.save(job);
      }
    });
  }

  private boolean hasActiveRun(long jobId) {
    ScanJobEntity job = getJobEntity(jobId);
    if (job.getActiveRunId() != null) {
      ScanRunEntity active = scanRunRepository.findById(job.getActiveRunId()).orElse(null);
      if (active != null && ACTIVE_STATUSES.contains(active.getStatus())) {
        return true;
      }
    }
    return scanRunRepository
        .findFirstByScanJobIdAndStatusInOrderByIdDesc(jobId, ACTIVE_STATUSES)
        .isPresent();
  }

  private ScanRunEntity createRunEntity(
      ScanRunSource source,
      Long scanJobId,
      String requestJson,
      int totalAddresses
  ) {
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    ScanRunEntity entity = new ScanRunEntity();
    entity.setSource(source);
    entity.setScanJobId(scanJobId);
    entity.setRequestJson(requestJson);
    entity.setStatus(ScanRunStatus.QUEUED);
    entity.setTotalAddresses(totalAddresses);
    entity.setScannedAddresses(0);
    entity.setFoundCount(0);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    return entity;
  }

  private ScanRunEntity getRunEntity(long runId) {
    return scanRunRepository.findById(runId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Запуск сканирования не найден.")
    );
  }

  private ScanJobEntity getJobEntity(long jobId) {
    return scanJobRepository.findById(jobId).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Задача автосканирования не найдена.")
    );
  }

  private ScanRunStartResponse toStartResponse(ScanRunEntity entity) {
    return new ScanRunStartResponse(
        entity.getId(),
        entity.getScanJobId(),
        entity.getStatus(),
        entity.getTotalAddresses()
    );
  }

  private ScanRunDto toDto(ScanRunEntity entity) {
    return new ScanRunDto(
        entity.getId(),
        entity.getSource(),
        entity.getScanJobId(),
        entity.getStatus(),
        entity.getTotalAddresses(),
        entity.getScannedAddresses(),
        entity.getFoundCount(),
        entity.getErrorMessage(),
        entity.getStartedAt(),
        entity.getFinishedAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  private String writeScanRequestJson(ScanRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось сериализовать параметры сканирования.", exception);
    }
  }

  private ScanRequest readScanRequestJson(String json) {
    try {
      return objectMapper.readValue(json, ScanRequest.class);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Не удалось прочитать параметры сканирования.",
          exception
      );
    }
  }

  private ScanJobRequest readJobRequestJson(String json) {
    try {
      ScanJobRequest parsed = objectMapper.readValue(json, ScanJobRequest.class);
      if (parsed == null || parsed.scan() == null) {
        ScanRequest scan = objectMapper.readValue(json, ScanRequest.class);
        return new ScanJobRequest(scan, false, List.of());
      }
      return parsed;
    } catch (JsonProcessingException exception) {
      try {
        ScanRequest scan = objectMapper.readValue(json, ScanRequest.class);
        return new ScanJobRequest(scan, false, List.of());
      } catch (JsonProcessingException legacyException) {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Не удалось прочитать сохранённые параметры сканирования.",
            exception
        );
      }
    }
  }

  private String writeResultJson(List<DeviceScanResult> results) {
    try {
      return objectMapper.writeValueAsString(results);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Не удалось сохранить результат сканирования.", exception);
    }
  }

  private List<DeviceScanResult> readResultJson(String json) {
    if (json == null || json.isBlank()) {
      return List.of();
    }
    try {
      List<DeviceScanResult> results = objectMapper.readValue(json, DEVICE_SCAN_RESULT_LIST);
      return results != null ? results : List.of();
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Не удалось прочитать результат сканирования.",
          exception
      );
    }
  }

  private List<DeviceScanResult> computeDiscoveredNotMonitoredDevices() {
    Map<String, DeviceScanResult> byIp = new LinkedHashMap<>();
    for (ScanJobEntity job : scanJobRepository.findAll()) {
      String json = job.getLastResultJson();
      if (json == null || json.isBlank()) {
        continue;
      }
      try {
        List<DeviceScanResult> list = objectMapper.readValue(json, DEVICE_SCAN_RESULT_LIST);
        for (DeviceScanResult device : list) {
          if (device == null || device.ip() == null || device.ip().isBlank()) {
            continue;
          }
          byIp.putIfAbsent(device.ip().trim(), device);
        }
      } catch (JsonProcessingException ignored) {
        // skip malformed job result
      }
    }
    if (byIp.isEmpty()) {
      return List.of();
    }
    Set<String> monitored = monitoredDeviceIpLookup.findMonitoredIpsIn(byIp.keySet());
    return byIp.entrySet().stream()
        .filter(e -> !monitored.contains(e.getKey()))
        .map(Map.Entry::getValue)
        .sorted(Comparator.comparing(DeviceScanResult::ip))
        .toList();
  }

  private MonitoringSnmpCredentials snmpCredentialsForScan(ScanRequest scan) {
    if (scan != null && scan.accessProfileId() != null) {
      DiscoveryProbeConfig snmpProbe = scan.preferredSnmpProbe();
      MonitoringSnmpCredentials fromProfile = accessProfileResolver.resolveSnmpCredentials(
          scan.accessProfileId(),
          snmpProbe != null ? snmpProbe.method() : null
      );
      if (fromProfile != null) {
        return fromProfile;
      }
    }
    return snmpCredentialsFromScan(scan);
  }

  private static MonitoringSnmpCredentials snmpCredentialsFromScan(ScanRequest scan) {
    if (scan == null) {
      return null;
    }
    DiscoveryProbeConfig snmpProbe = scan.preferredSnmpProbe();
    if (snmpProbe != null) {
      return new MonitoringSnmpCredentials(
          ScanRequest.snmpVersionForProbe(snmpProbe),
          snmpProbe.community(),
          snmpProbe.securityUsername(),
          snmpProbe.authProtocol(),
          snmpProbe.authPassword(),
          snmpProbe.privacyProtocol(),
          snmpProbe.privacyPassword()
      );
    }
    if (scan.scanMode() == null || scan.scanMode().isBlank()) {
      return null;
    }
    String snmpVersion = scan.snmpVersion() != null && !scan.snmpVersion().isBlank()
        ? scan.snmpVersion()
        : ScanRequest.snmpVersionForProbe(scan.effectiveProbes().get(0));
    return new MonitoringSnmpCredentials(
        snmpVersion,
        scan.community(),
        scan.securityUsername(),
        scan.authProtocol(),
        scan.authPassword(),
        scan.privacyProtocol(),
        scan.privacyPassword()
    );
  }
}
