package com.networkscanner.backend.workstation.impl;

import com.networkscanner.backend.agentingest.impl.AgentIngestServiceImpl;
import com.networkscanner.backend.monitoring.api.MetricsHistoryService;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.workstation.api.WorkstationPort;
import com.networkscanner.backend.workstation.dto.WorkstationDetailDto;
import com.networkscanner.backend.workstation.dto.WorkstationEventEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationFilter;
import com.networkscanner.backend.workstation.dto.WorkstationListItemDto;
import com.networkscanner.backend.workstation.dto.WorkstationLogEntryDto;
import com.networkscanner.backend.workstation.dto.WorkstationMetricPointDto;
import com.networkscanner.backend.workstation.dto.WorkstationMetricSeriesDto;
import com.networkscanner.backend.workstation.dto.WorkstationMetricsHistoryDto;
import com.networkscanner.backend.workstation.dto.WorkstationPageDto;
import com.networkscanner.backend.workstation.model.WorkstationEntity;
import com.networkscanner.backend.workstation.repository.WorkstationRepository;
import com.networkscanner.backend.workstation.repository.WorkstationTelemetryRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WorkstationServiceImpl implements WorkstationPort {

  private static final int DEFAULT_PAGE_SIZE = 25;
  private static final int MAX_PAGE_SIZE = 200;
  private static final int DEFAULT_METRICS_MAX_POINTS = 600;

  private static final int DEFAULT_LOG_LIMIT = 50;
  private static final int DEFAULT_EVENT_LIMIT = 30;
  private static final int MAX_LOG_LIMIT = 200;
  private static final int MAX_EVENT_LIMIT = 100;

  private final WorkstationRepository workstationRepository;
  private final MetricsHistoryService metricsHistoryService;
  private final WorkstationTelemetryRepository telemetryRepository;
  private final int offlineThresholdMinutes;

  public WorkstationServiceImpl(
      WorkstationRepository workstationRepository,
      MetricsHistoryService metricsHistoryService,
      WorkstationTelemetryRepository telemetryRepository,
      @Value("${app.workstation.offline-threshold-minutes:5}") int offlineThresholdMinutes
  ) {
    this.workstationRepository = workstationRepository;
    this.metricsHistoryService = metricsHistoryService;
    this.telemetryRepository = telemetryRepository;
    this.offlineThresholdMinutes = offlineThresholdMinutes;
  }

  @Override
  @Transactional(readOnly = true)
  public WorkstationPageDto list(WorkstationFilter filter, int page, int size, String sortField, String sortOrder) {
    int safePage = Math.max(page, 0);
    int pageSize = size <= 0 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE);
    Pageable pageable = PageRequest.of(safePage, pageSize, WorkstationSortSupport.buildSort(sortField, sortOrder));
    OffsetDateTime now = OffsetDateTime.now();
    Specification<WorkstationEntity> spec = WorkstationSpecifications.fromFilter(filter, offlineThresholdMinutes, now);
    Page<WorkstationEntity> entityPage = workstationRepository.findAll(spec, pageable);

    List<WorkstationListItemDto> content = entityPage.getContent().stream()
        .map(entity -> toListItem(entity, now))
        .toList();

    Specification<WorkstationEntity> baseSpec = WorkstationSpecifications.fromFilter(
        new WorkstationFilter(filter == null ? null : filter.q(), null, filter == null ? null : filter.osType()),
        offlineThresholdMinutes,
        now
    );
    long onlineCount = workstationRepository.count(
        baseSpec.and(WorkstationSpecifications.effectiveOnline(offlineThresholdMinutes, now))
    );
    long total = workstationRepository.count(baseSpec);
    long offlineCount = total - onlineCount;

    return new WorkstationPageDto(
        content,
        entityPage.getTotalElements(),
        entityPage.getTotalPages(),
        entityPage.getNumber(),
        entityPage.getSize(),
        entityPage.isFirst(),
        entityPage.isLast(),
        onlineCount,
        offlineCount
    );
  }

  @Override
  @Transactional(readOnly = true)
  public WorkstationDetailDto getById(long id) {
    WorkstationEntity entity = workstationRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("АРМ не найден: " + id));
    OffsetDateTime now = OffsetDateTime.now();
    return new WorkstationDetailDto(
        entity.getId(),
        entity.getHostname(),
        entity.getDisplayName(),
        entity.getOsType(),
        entity.getPrimaryIp(),
        entity.getAgentVersion(),
        WorkstationStatusSupport.effectiveStatus(entity, offlineThresholdMinutes, now),
        entity.getLastSeenAt(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  @Override
  @Transactional(readOnly = true)
  public WorkstationMetricsHistoryDto getMetricsHistory(
      long id,
      OffsetDateTime from,
      OffsetDateTime to,
      Integer maxPoints
  ) {
    WorkstationEntity entity = workstationRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("АРМ не найден: " + id));
    OffsetDateTime effectiveTo = to == null ? OffsetDateTime.now() : to;
    OffsetDateTime effectiveFrom = from == null ? effectiveTo.minusDays(1) : from;
    if (effectiveFrom.isAfter(effectiveTo)) {
      effectiveFrom = effectiveTo.minusHours(1);
    }
    String deviceKey = AgentIngestServiceImpl.metricDeviceKey(entity);
    List<String> metricKeys = ArmMetricCatalog.ARM_METRICS.stream().map(ArmMetricCatalog.Definition::key).toList();
    int effectiveMaxPoints = maxPoints == null || maxPoints <= 0 ? DEFAULT_METRICS_MAX_POINTS : maxPoints;
    List<MetricValueDto> values = metricsHistoryService.queryMetricValues(
        deviceKey,
        effectiveFrom,
        effectiveTo,
        metricKeys,
        effectiveMaxPoints
    );
    Map<String, List<MetricValueDto>> grouped = values.stream()
        .collect(Collectors.groupingBy(MetricValueDto::metricName));
    List<WorkstationMetricSeriesDto> series = new ArrayList<>();
    for (ArmMetricCatalog.Definition definition : ArmMetricCatalog.ARM_METRICS) {
      List<WorkstationMetricPointDto> points = grouped.getOrDefault(definition.key(), List.of()).stream()
          .sorted((left, right) -> left.recordedAt().compareTo(right.recordedAt()))
          .map(point -> new WorkstationMetricPointDto(point.recordedAt(), point.metricValue()))
          .toList();
      series.add(new WorkstationMetricSeriesDto(
          definition.key(),
          definition.displayName(),
          definition.unit(),
          points
      ));
    }
    return new WorkstationMetricsHistoryDto(deviceKey, effectiveFrom, effectiveTo, series);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkstationLogEntryDto> getLogs(long id, List<String> levels, int limit) {
    requireWorkstation(id);
    List<String> normalizedLevels = normalizeLogLevels(levels);
    int effectiveLimit = limit <= 0 ? DEFAULT_LOG_LIMIT : Math.min(limit, MAX_LOG_LIMIT);
    return telemetryRepository.findLogs(id, normalizedLevels, effectiveLimit);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WorkstationEventEntryDto> getEvents(long id, int limit) {
    requireWorkstation(id);
    int effectiveLimit = limit <= 0 ? DEFAULT_EVENT_LIMIT : Math.min(limit, MAX_EVENT_LIMIT);
    return telemetryRepository.findEvents(id, effectiveLimit);
  }

  private WorkstationEntity requireWorkstation(long id) {
    return workstationRepository.findById(id)
        .orElseThrow(() -> new IllegalArgumentException("АРМ не найден: " + id));
  }

  private static List<String> normalizeLogLevels(List<String> levels) {
    if (levels == null || levels.isEmpty()) {
      return List.of("WARNING", "ERROR");
    }
    return levels.stream()
        .filter(level -> level != null && !level.isBlank())
        .map(level -> level.trim().toUpperCase())
        .distinct()
        .toList();
  }

  private WorkstationListItemDto toListItem(WorkstationEntity entity, OffsetDateTime now) {
    return new WorkstationListItemDto(
        entity.getId(),
        entity.getHostname(),
        entity.getDisplayName(),
        entity.getOsType(),
        entity.getPrimaryIp(),
        entity.getAgentVersion(),
        WorkstationStatusSupport.effectiveStatus(entity, offlineThresholdMinutes, now),
        entity.getLastSeenAt()
    );
  }
}
