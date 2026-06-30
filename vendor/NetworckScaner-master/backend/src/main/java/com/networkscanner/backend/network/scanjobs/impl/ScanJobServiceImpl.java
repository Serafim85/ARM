package com.networkscanner.backend.network.scanjobs.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.monitoring.api.MonitoredDeviceIpLookup;
import com.networkscanner.backend.network.scan.api.ScanRunService;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import com.networkscanner.backend.network.scan.dto.ScanRunStartResponse;
import com.networkscanner.backend.network.scan.model.ScanRunEntity;
import com.networkscanner.backend.network.scan.model.ScanRunStatus;
import com.networkscanner.backend.network.scan.repository.ScanRunRepository;
import com.networkscanner.backend.network.scanjobs.api.ScanJobService;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobChangedEvent;
import com.networkscanner.backend.network.scanjobs.dto.DiscoveredNotMonitoredSummaryDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobDetailsDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobMetaUpdateRequest;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobRequest;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobUpsertRequest;
import com.networkscanner.backend.network.scanjobs.model.ScanJobEntity;
import com.networkscanner.backend.network.scanjobs.model.ScanJobStatus;
import com.networkscanner.backend.network.scanjobs.repository.ScanJobRepository;
import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;

@Service
public class ScanJobServiceImpl implements ScanJobService {

  private static final Logger log = LoggerFactory.getLogger(ScanJobServiceImpl.class);

  private static final TypeReference<List<DeviceScanResult>> DEVICE_SCAN_RESULT_LIST =
      new TypeReference<>() {};

  private final ScanJobRepository repository;
  private final ObjectMapper objectMapper;
  private final ScanRunService scanRunService;
  private final ScanRunRepository scanRunRepository;
  private final ApplicationEventPublisher events;
  private final MonitoredDeviceIpLookup monitoredDeviceIpLookup;
  private final AuditLogService auditLogService;

  public ScanJobServiceImpl(
      ScanJobRepository repository,
      ObjectMapper objectMapper,
      ScanRunService scanRunService,
      ScanRunRepository scanRunRepository,
      ApplicationEventPublisher events,
      MonitoredDeviceIpLookup monitoredDeviceIpLookup,
      AuditLogService auditLogService
  ) {
    this.repository = repository;
    this.objectMapper = objectMapper;
    this.scanRunService = scanRunService;
    this.scanRunRepository = scanRunRepository;
    this.events = events;
    this.monitoredDeviceIpLookup = monitoredDeviceIpLookup;
    this.auditLogService = auditLogService;
  }

  @Override
  public List<ScanJobDto> list() {
    return repository.findAll().stream()
        .sorted(Comparator.comparing(ScanJobEntity::getId))
        .map(this::toDto)
        .toList();
  }

  @Override
  public ScanJobDto get(long id) {
    return toDto(getEntity(id));
  }

  @Override
  public ScanJobDetailsDto getDetails(long id) {
    ScanJobEntity entity = getEntity(id);
    return toDetailsDto(entity);
  }

  @Override
  @Transactional
  public ScanJobDto create(ScanJobUpsertRequest request, Authentication authentication) {
    validateCron(request.cron());
    ScanJobEntity entity = new ScanJobEntity();
    entity.setName(request.name().trim());
    entity.setEnabled(request.enabled());
    entity.setCron(request.cron().trim());
    entity.setRequestJson(writeRequestJson(request.request()));
    entity.setLastResultCount(0);
    OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
    entity.setCreatedAt(now);
    entity.setUpdatedAt(now);
    ScanJobEntity saved = repository.save(entity);
    events.publishEvent(new ScanJobChangedEvent(saved.getId(), saved.isEnabled()));
    auditLogService.record(
        authentication,
        AuditCategory.SCAN_JOB,
        AuditAction.CREATE,
        "id=" + saved.getId() + ", name=" + saved.getName(),
        null
    );
    return toDto(saved);
  }

  @Override
  @Transactional
  public ScanJobDto update(long id, ScanJobUpsertRequest request, Authentication authentication) {
    validateCron(request.cron());
    ScanJobEntity entity = getEntity(id);
    entity.setName(request.name().trim());
    entity.setEnabled(request.enabled());
    entity.setCron(request.cron().trim());
    entity.setRequestJson(writeRequestJson(request.request()));
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    ScanJobEntity saved = repository.save(entity);
    events.publishEvent(new ScanJobChangedEvent(saved.getId(), saved.isEnabled()));
    auditLogService.record(
        authentication,
        AuditCategory.SCAN_JOB,
        AuditAction.UPDATE,
        "id=" + saved.getId() + ", name=" + saved.getName(),
        "Обновлены параметры сканирования и расписание"
    );
    return toDto(saved);
  }

  @Override
  @Transactional
  public ScanJobDto updateMeta(long id, ScanJobMetaUpdateRequest request, Authentication authentication) {
    validateCron(request.cron());
    ScanJobEntity entity = getEntity(id);
    entity.setName(request.name().trim());
    entity.setEnabled(request.enabled());
    entity.setCron(request.cron().trim());
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    ScanJobEntity saved = repository.save(entity);
    events.publishEvent(new ScanJobChangedEvent(saved.getId(), saved.isEnabled()));
    auditLogService.record(
        authentication,
        AuditCategory.SCAN_JOB,
        AuditAction.UPDATE,
        "id=" + saved.getId() + ", name=" + saved.getName(),
        "Обновлены имя, CRON и состояние включения"
    );
    return toDto(saved);
  }

  @Override
  @Transactional
  public ScanJobDto setEnabled(long id, boolean enabled, Authentication authentication) {
    ScanJobEntity entity = getEntity(id);
    entity.setEnabled(enabled);
    entity.setUpdatedAt(OffsetDateTime.now(ZoneOffset.UTC));
    ScanJobEntity saved = repository.save(entity);
    events.publishEvent(new ScanJobChangedEvent(saved.getId(), enabled));
    auditLogService.record(
        authentication,
        AuditCategory.SCAN_JOB,
        AuditAction.UPDATE,
        "id=" + saved.getId() + ", name=" + saved.getName(),
        enabled ? "Задача включена" : "Задача выключена"
    );
    return toDto(saved);
  }

  @Override
  public List<DeviceScanResult> getLastResult(long id) {
    ScanJobEntity entity = getEntity(id);
    if (entity.getLastResultJson() == null || entity.getLastResultJson().isBlank()) {
      return List.of();
    }
    try {
      return objectMapper.readValue(entity.getLastResultJson(), DEVICE_SCAN_RESULT_LIST);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR,
          "Не удалось прочитать сохранённый результат последнего сканирования.",
          exception
      );
    }
  }

  @Override
  public DiscoveredNotMonitoredSummaryDto getDiscoveredNotMonitoredSummary() {
    List<DeviceScanResult> devices = computeDiscoveredNotMonitoredDevices();
    return new DiscoveredNotMonitoredSummaryDto(devices.size());
  }

  @Override
  public List<DeviceScanResult> getDiscoveredNotMonitoredDevices() {
    return computeDiscoveredNotMonitoredDevices();
  }

  /**
   * Уникальные IP из последних результатов всех задач (при дубликате IP — запись из задачи с большим id,
   * внутри одной задачи — последняя по порядку в JSON), минус IP уже на мониторинге. Сортировка по IP.
   */
  private List<DeviceScanResult> computeDiscoveredNotMonitoredDevices() {
    Map<String, DeviceScanResult> byIp = new LinkedHashMap<>();
    List<ScanJobEntity> jobs = repository.findAll().stream()
        .sorted(Comparator.comparing(ScanJobEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())))
        .toList();
    for (ScanJobEntity job : jobs) {
      String json = job.getLastResultJson();
      if (json == null || json.isBlank()) {
        continue;
      }
      try {
        List<DeviceScanResult> list = objectMapper.readValue(json, DEVICE_SCAN_RESULT_LIST);
        for (DeviceScanResult r : list) {
          if (r == null || r.ip() == null) {
            continue;
          }
          String ip = r.ip().trim();
          if (ip.isEmpty()) {
            continue;
          }
          byIp.put(ip, r);
        }
      } catch (JsonProcessingException exception) {
        continue;
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

  @Override
  public ScanRunStartResponse runNow(long id) {
    return scanRunService.startForJob(id, true)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.CONFLICT,
            "Сканирование уже выполняется."
        ));
  }

  @Override
  @Transactional
  public void delete(long id, Authentication authentication) {
    ScanJobEntity entity = getEntity(id);
    String name = entity.getName();
    // Сначала снимаем задачу с планировщика (через event listener).
    events.publishEvent(new ScanJobChangedEvent(entity.getId(), false));
    repository.delete(entity);
    auditLogService.record(
        authentication,
        AuditCategory.SCAN_JOB,
        AuditAction.DELETE,
        "id=" + id + ", name=" + name,
        null
    );
  }

  private ScanJobEntity getEntity(long id) {
    return repository.findById(id).orElseThrow(() ->
        new ResponseStatusException(HttpStatus.NOT_FOUND, "Задача автосканирования не найдена.")
    );
  }

  private ScanJobDto toDto(ScanJobEntity entity) {
    int scannedAddresses = 0;
    int totalAddresses = 0;
    Long activeRunId = entity.getActiveRunId();
    ScanJobStatus lastStatus = entity.getLastStatus();
    if (activeRunId != null) {
      ScanRunEntity activeRun = scanRunRepository.findById(activeRunId).orElse(null);
      if (activeRun != null) {
        scannedAddresses = activeRun.getScannedAddresses();
        totalAddresses = activeRun.getTotalAddresses();
        if (isActiveRunStatus(activeRun.getStatus())) {
          lastStatus = ScanJobStatus.RUNNING;
        } else if (lastStatus == ScanJobStatus.RUNNING) {
          lastStatus = ScanJobStatus.FAILED;
          activeRunId = null;
        }
      } else if (lastStatus == ScanJobStatus.RUNNING) {
        lastStatus = ScanJobStatus.FAILED;
        activeRunId = null;
      }
    }
    return new ScanJobDto(
        entity.getId(),
        entity.getName(),
        entity.isEnabled(),
        entity.getCron(),
        entity.getLastRunAt(),
        lastStatus,
        entity.getLastError(),
        entity.getLastResultCount(),
        computeDiscoveredNotMonitoredCount(entity),
        activeRunId,
        scannedAddresses,
        totalAddresses,
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  private static boolean isActiveRunStatus(ScanRunStatus status) {
    return status == ScanRunStatus.QUEUED || status == ScanRunStatus.RUNNING;
  }

  private ScanJobDetailsDto toDetailsDto(ScanJobEntity entity) {
    ScanJobRequest request = readRequestJson(entity.getRequestJson());
    return new ScanJobDetailsDto(
        entity.getId(),
        entity.getName(),
        entity.isEnabled(),
        entity.getCron(),
        request,
        entity.getLastRunAt(),
        entity.getLastStatus(),
        entity.getLastError(),
        entity.getLastResultCount(),
        computeDiscoveredNotMonitoredCount(entity),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  /**
   * Количество уникальных IP из последнего результата задачи, которые ещё не на мониторинге.
   * Ошибки/пустой JSON трактуем как 0, чтобы список задач всегда был доступен.
   */
  private int computeDiscoveredNotMonitoredCount(ScanJobEntity job) {
    String json = job.getLastResultJson();
    if (json == null || json.isBlank()) {
      return 0;
    }
    Set<String> ips = new HashSet<>();
    try {
      List<DeviceScanResult> list = objectMapper.readValue(json, DEVICE_SCAN_RESULT_LIST);
      for (DeviceScanResult r : list) {
        if (r == null || r.ip() == null) {
          continue;
        }
        String ip = r.ip().trim();
        if (!ip.isEmpty()) {
          ips.add(ip);
        }
      }
    } catch (JsonProcessingException exception) {
      return 0;
    }
    if (ips.isEmpty()) {
      return 0;
    }
    Set<String> monitored = monitoredDeviceIpLookup.findMonitoredIpsIn(ips);
    int count = 0;
    for (String ip : ips) {
      if (!monitored.contains(ip)) {
        count++;
      }
    }
    return count;
  }

  private void validateCron(String cron) {
    if (cron == null || cron.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CRON-выражение обязательно.");
    }
    try {
      CronExpression.parse(cron.trim());
    } catch (IllegalArgumentException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректное CRON-выражение.", exception);
    }
  }

  private String writeRequestJson(ScanJobRequest request) {
    try {
      return objectMapper.writeValueAsString(request);
    } catch (JsonProcessingException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось сериализовать параметры сканирования.", exception);
    }
  }

  private ScanJobRequest readRequestJson(String json) {
    try {
      ScanJobRequest parsed = objectMapper.readValue(json, ScanJobRequest.class);
      // Backward compatibility: older versions stored plain ScanRequest JSON in request_json.
      // In that case Jackson can deserialize ScanJobRequest with scan=null (no "scan" field), so we must detect it.
      if (parsed == null || parsed.scan() == null) {
        ScanRequest scan = objectMapper.readValue(json, ScanRequest.class);
        return new ScanJobRequest(scan, false, List.of());
      }
      return parsed;
    } catch (JsonProcessingException exception) {
      // Backward compatibility: older versions stored plain ScanRequest JSON in request_json.
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
}

