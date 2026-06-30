package com.networkscanner.backend.monitoring.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.event.WislaMonitorStateSnapshotEvent;
import com.networkscanner.backend.integration.impl.MonitorStateSnapshotMapper;
import com.networkscanner.backend.inventory.api.ConfigBackupService;
import com.networkscanner.backend.monitoring.api.MetricsHistoryService;
import com.networkscanner.backend.monitoring.api.MonitoredDeviceItemService;
import com.networkscanner.backend.monitoring.api.MonitoringService;
import com.networkscanner.backend.monitoring.api.MonitoringTemplateResolver;
import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.AvailabilityDto;
import com.networkscanner.backend.monitoring.dto.CompactMetricsBatchSeriesDto;
import com.networkscanner.backend.monitoring.dto.CompactMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.DeviceMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.DiscoveryInstanceRuntime;
import com.networkscanner.backend.monitoring.dto.ItemStateSnapshot;
import com.networkscanner.backend.monitoring.dto.DeviceInterfaceDto;
import com.networkscanner.backend.monitoring.dto.MetricDefinition;
import com.networkscanner.backend.monitoring.dto.MetricChartPanelDto;
import com.networkscanner.backend.monitoring.dto.MetricChartThresholdDto;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.MonitoredDeviceDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDiscoveryInstanceDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemsUpdateRequest;
import com.networkscanner.backend.monitoring.dto.ItemStateTelemetrySnapshot;
import com.networkscanner.backend.monitoring.dto.MonitoringDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricDto;
import com.networkscanner.backend.monitoring.dto.MonitoringEventDto;
import com.networkscanner.backend.monitoring.dto.MonitoringEventFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringEventLevelSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringEventPageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringHostAvailabilityFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringHostFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringHostPageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringHostRowDto;
import com.networkscanner.backend.monitoring.dto.MonitoringItemStateDto;
import com.networkscanner.backend.monitoring.dto.MonitoringItemStatePageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchSeriesDto;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchSeriesRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateImportPreviewDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOperationResultDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSource;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateUpdateRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringSnmpCredentials;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ValueMapSeriesMeta;
import com.networkscanner.backend.monitoring.mapper.ChartCompactMapper;
import com.networkscanner.backend.monitoring.util.MonitoringSnmpTemplateSupport;
import com.networkscanner.backend.monitoring.util.ValueMapSeriesResolver;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.UnitDefinition;
import com.networkscanner.backend.monitoring.dto.UploadedMonitoringTemplatePackage;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceInterfaceEntity;
import com.networkscanner.backend.monitoring.model.MonitoringEventEntity;
import com.networkscanner.backend.monitoring.model.MonitoringTelemetrySnapshotEntity;
import com.networkscanner.backend.monitoring.model.MonitoringTemplatePriorityOverrideEntity;
import com.networkscanner.backend.monitoring.model.UploadedMonitoringTemplateEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceInterfaceRepository;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import com.networkscanner.backend.monitoring.repository.MonitoringTelemetrySnapshotRepository;
import com.networkscanner.backend.monitoring.repository.MonitoringTemplatePriorityOverrideRepository;
import com.networkscanner.backend.monitoring.repository.UploadedMonitoringTemplateRepository;
import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.util.SnmpDeviceTypeClassifier;
import jakarta.annotation.PostConstruct;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MonitoringServiceImpl implements MonitoringService {

  private static final Logger log = LoggerFactory.getLogger(MonitoringServiceImpl.class);

  private static final int DEFAULT_EVENT_PAGE_SIZE = 20;
  private static final int MAX_EVENT_PAGE_SIZE = 200;
  private static final int DEFAULT_ITEM_STATE_PAGE_SIZE = 20;
  private static final int MAX_ITEM_STATE_PAGE_SIZE = 200;
  private static final int DEFAULT_HOST_PAGE_SIZE = 15;
  private static final int MAX_HOST_PAGE_SIZE = 200;
  private static final int MAX_METRICS_BATCH_SERIES = 20;
  /** Максимум панелей в одном ответе при явном {@code panelsLimit}. */
  private static final int MAX_CHART_PANEL_PAGE = 100;
  private static final String DETAILS_SOURCE_BOOTSTRAP = "BOOTSTRAP";
  private static final String DETAILS_SOURCE_MANUAL_REFRESH = "MANUAL_REFRESH";
  private static final String DETAILS_SOURCE_LIVE_REFRESH = "LIVE_REFRESH";

  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

  private final SnmpScanService scanService;
  private final ObjectMapper objectMapper;
  private final ConfigBackupService configBackupService;
  private final MetricsHistoryService metricsHistoryService;
  private final MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository;
  private final MonitoredDeviceRepository monitoredDeviceRepository;
  private final MonitoringEventRepository monitoringEventRepository;
  private final MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository;
  private final UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository;
  private final MonitoringTemplatePriorityOverrideRepository priorityOverrideRepository;
  private final MonitoringTemplateResolver templateResolver;
  private final MonitoringTemplateArchiveReader templateArchiveReader;
  private final ZabbixRuntimeStateService runtimeStateService;
  private final MonitoredDeviceItemService monitoredDeviceItemService;
  private final UnitScalingService unitScalingService;
  private final AuditLogService auditLogService;
  private final MetricChartLayoutBuilder metricChartLayoutBuilder = new MetricChartLayoutBuilder();
  private final MetricChartThresholdBuilder metricChartThresholdBuilder;
  private final MonitorStateSnapshotMapper monitorStateSnapshotMapper = new MonitorStateSnapshotMapper();
  @Autowired
  private ApplicationEventPublisher applicationEventPublisher;
  @Autowired(required = false)
  private SourceSystemProvider sourceSystemProvider;

  public MonitoringServiceImpl(
      SnmpScanService scanService,
      ObjectMapper objectMapper,
      ConfigBackupService configBackupService,
      MetricsHistoryService metricsHistoryService,
      MonitoredDeviceInterfaceRepository monitoredDeviceInterfaceRepository,
      MonitoredDeviceRepository monitoredDeviceRepository,
      MonitoringEventRepository monitoringEventRepository,
      MonitoringTelemetrySnapshotRepository monitoringTelemetrySnapshotRepository,
      UploadedMonitoringTemplateRepository uploadedMonitoringTemplateRepository,
      MonitoringTemplatePriorityOverrideRepository priorityOverrideRepository,
      MonitoringTemplateResolver templateResolver,
      MonitoringTemplateArchiveReader templateArchiveReader,
      ZabbixRuntimeStateService runtimeStateService,
      MonitoredDeviceItemService monitoredDeviceItemService,
      UnitScalingService unitScalingService,
      AuditLogService auditLogService
  ) {
    this.scanService = scanService;
    this.objectMapper = objectMapper;
    this.configBackupService = configBackupService;
    this.metricsHistoryService = metricsHistoryService;
    this.monitoredDeviceInterfaceRepository = monitoredDeviceInterfaceRepository;
    this.monitoredDeviceRepository = monitoredDeviceRepository;
    this.monitoringEventRepository = monitoringEventRepository;
    this.monitoringTelemetrySnapshotRepository = monitoringTelemetrySnapshotRepository;
    this.uploadedMonitoringTemplateRepository = uploadedMonitoringTemplateRepository;
    this.priorityOverrideRepository = priorityOverrideRepository;
    this.templateResolver = templateResolver;
    this.templateArchiveReader = templateArchiveReader;
    this.runtimeStateService = runtimeStateService;
    this.monitoredDeviceItemService = monitoredDeviceItemService;
    this.unitScalingService = unitScalingService;
    this.auditLogService = auditLogService;
    this.metricChartThresholdBuilder = new MetricChartThresholdBuilder(runtimeStateService, unitScalingService);
  }

  @Override
  @PostConstruct
  public synchronized void initialize() {
    configBackupService.ensureBackupsForDevices(listStoredDevices());
  }

  @Override
  @Transactional(readOnly = true)
  public MonitoringHostPageDto list(
      MonitoringHostFilter filter,
      int page,
      int size,
      String sortField,
      String sortOrder
  ) {
    int safePage = Math.max(page, 0);
    int pageSize = size <= 0 ? DEFAULT_HOST_PAGE_SIZE : size;
    pageSize = Math.min(pageSize, MAX_HOST_PAGE_SIZE);
    Pageable pageable = PageRequest.of(safePage, pageSize, MonitoredHostSortSupport.buildHostSort(sortField, sortOrder));

    Specification<MonitoredDeviceEntity> fullSpec = MonitoredDeviceSpecifications.fromFilter(filter);
    Specification<MonitoredDeviceEntity> summarySpec = MonitoredDeviceSpecifications.withoutAvailability(filter);
    Page<MonitoredDeviceEntity> entityPage = monitoredDeviceRepository.findAll(fullSpec, pageable);

    List<MonitoringHostRowDto> content = entityPage.getContent().stream()
        .map(this::toMonitoringHostRow)
        .toList();

    return new MonitoringHostPageDto(
        content,
        entityPage.getTotalElements(),
        entityPage.getTotalPages(),
        entityPage.getNumber(),
        entityPage.getSize(),
        entityPage.isFirst(),
        entityPage.isLast(),
        countHostsByAvailability(summarySpec, MonitoringHostAvailabilityFilter.AVAILABLE),
        countHostsByAvailability(summarySpec, MonitoringHostAvailabilityFilter.UNAVAILABLE),
        countHostsByAvailability(summarySpec, MonitoringHostAvailabilityFilter.UNKNOWN)
    );
  }

  @Override
  @Transactional
  public synchronized List<DeviceScanResult> activate(
      List<DeviceScanResult> devices,
      String templateId,
      List<String> templateIds,
      Map<String, String> perDeviceTemplateIds,
      Map<String, List<String>> perDeviceTemplateIdLists,
      MonitoringSnmpCredentials snmpCredentials,
      Authentication authentication
  ) {
    Map<Long, MonitoredDeviceEntity> byExistingId = new LinkedHashMap<>();
    Map<String, MonitoredDeviceEntity> byNewIp = new LinkedHashMap<>();
    List<Map.Entry<String, String>> pendingIpMigrations = new ArrayList<>();
    Map<MonitoredDeviceEntity, List<String>> beforeTemplatesByEntity = new LinkedHashMap<>();

    for (DeviceScanResult device : devices) {
      List<String> selectedTemplateIds = resolveTemplateSelection(
          device.ip(),
          templateId,
          templateIds,
          perDeviceTemplateIds,
          perDeviceTemplateIdLists
      );
      MonitoredDeviceEntity entity = resolveEntityForActivation(
          device,
          selectedTemplateIds,
          pendingIpMigrations,
          beforeTemplatesByEntity,
          snmpCredentials
      );

      if (entity.getId() != null) {
        byExistingId.putIfAbsent(entity.getId(), entity);
      } else {
        byNewIp.putIfAbsent(device.ip(), entity);
      }
    }

    List<MonitoredDeviceEntity> toSave = new ArrayList<>(byExistingId.values());
    toSave.addAll(byNewIp.values());
    monitoredDeviceRepository.saveAll(toSave);
    monitoredDeviceRepository.flush();

    for (MonitoredDeviceEntity entity : toSave) {
      ResolvedMonitoringTemplate resolvedTemplate = templateResolver.resolveForDevice(
          MonitoringTemplateSelectionSupport.parseStored(entity.getTemplateIds(), entity.getTemplateId()),
          entity.getVendor(),
          entity.getModel(),
          entity.getFirmwareVersion()
      );
      monitoredDeviceItemService.seedDefaultsForDevice(entity, resolvedTemplate);
      publishMonitoringStateDiff(
          entity,
          beforeTemplatesByEntity.getOrDefault(entity, List.of()),
          MonitoringTemplateSelectionSupport.parseStored(entity.getTemplateIds(), entity.getTemplateId())
      );
    }

    for (Map.Entry<String, String> migration : pendingIpMigrations) {
      configBackupService.migrateDeviceIp(migration.getKey(), migration.getValue());
    }
    configBackupService.ensureBackupsForDevices(devices);
    int newCount = byNewIp.size();
    int updatedCount = byExistingId.size();
    if (newCount + updatedCount > 0) {
      auditLogService.record(
          authentication,
          AuditCategory.MONITORING_DEVICE,
          AuditAction.CREATE,
          "мониторинг устройств",
          "Новых записей: " + newCount + ", обновлено на мониторинге: " + updatedCount
      );
    }
    return listStoredDevices();
  }

  @Override
  @Transactional
  public synchronized List<DeviceScanResult> deactivate(List<String> ips, Authentication authentication) {
    List<MonitoredDeviceEntity> entities = monitoredDeviceRepository.findAllByIpIn(ips);
    emitDeletedForEntities(entities);
    List<Long> deviceIds = entities.stream().map(MonitoredDeviceEntity::getId).toList();
    if (!ips.isEmpty()) {
      configBackupService.removeDevices(ips);
    }
    if (!deviceIds.isEmpty()) {
      monitoringEventRepository.deleteByDevice_IdIn(deviceIds);
    }
    if (!ips.isEmpty()) {
      runtimeStateService.purgeDeviceHistory(ips);
    }
    if (!entities.isEmpty()) {
      monitoredDeviceRepository.deleteAll(entities);
    }
    if (!entities.isEmpty()) {
      auditLogService.record(
          authentication,
          AuditCategory.MONITORING_DEVICE,
          AuditAction.DELETE,
          "ips: " + summarizeIps(ips),
          "Удалено устройств с мониторинга: " + entities.size()
      );
    }
    return listStoredDevices();
  }

  @Override
  @Transactional
  public synchronized List<DeviceScanResult> deactivateByIds(List<Long> deviceIds, Authentication authentication) {
    List<MonitoredDeviceEntity> entities = monitoredDeviceRepository.findAllById(deviceIds);
    emitDeletedForEntities(entities);
    List<Long> existingDeviceIds = entities.stream().map(MonitoredDeviceEntity::getId).toList();
    List<String> ips = entities.stream().map(MonitoredDeviceEntity::getIp).toList();
    if (!ips.isEmpty()) {
      configBackupService.removeDevices(ips);
    }
    if (!existingDeviceIds.isEmpty()) {
      monitoringEventRepository.deleteByDevice_IdIn(existingDeviceIds);
    }
    if (!ips.isEmpty()) {
      runtimeStateService.purgeDeviceHistory(ips);
    }
    if (!entities.isEmpty()) {
      monitoredDeviceRepository.deleteAll(entities);
    }
    if (!entities.isEmpty()) {
      auditLogService.record(
          authentication,
          AuditCategory.MONITORING_DEVICE,
          AuditAction.DELETE,
          "deviceIds: " + summarizeIds(existingDeviceIds),
          "Удалено устройств с мониторинга: " + entities.size()
      );
    }
    return listStoredDevices();
  }

  @Override
  @Transactional(readOnly = true)
  public DeviceScanResult getByIp(String ip) {
    return monitoredDeviceRepository.findFirstByIpOrderByUpdatedAtDesc(ip)
        .map(this::toResult)
        .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено на мониторинге."));
  }

  @Override
  @Transactional(readOnly = true)
  public DeviceScanResult getByDeviceId(Long id) {
    return monitoredDeviceRepository.findById(id)
        .map(this::toResult)
        .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено на мониторинге."));
  }

  @Override
  @Transactional(readOnly = true)
  public MonitoredDeviceDto getMonitoredDeviceById(Long id) {
    return toMonitoredDeviceDto(findEntityById(id));
  }

  @Override
  @Transactional
  public MonitoredDeviceDto updateDeviceTags(Long deviceId, List<String> tags, Authentication authentication) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    entity.setTagsJson(writeTags(normalizeTags(tags)));
    entity.setUpdatedAt(OffsetDateTime.now());
    monitoredDeviceRepository.save(entity);
    auditLogService.record(
        authentication,
        AuditCategory.MONITORING_DEVICE,
        AuditAction.UPDATE,
        "deviceId=" + deviceId + ", ip=" + entity.getIp(),
        "Обновлены теги устройства"
    );
    return toMonitoredDeviceDto(entity);
  }

  @Override
  @Transactional
  public List<DeviceInterfaceDto> getDeviceInterfaces(String ip) {
    MonitoredDeviceEntity entity = monitoredDeviceRepository.findFirstByIpOrderByUpdatedAtDesc(ip)
        .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено на мониторинге."));
    return getStoredOrRefreshInterfaces(entity);
  }

  @Override
  @Transactional
  public List<DeviceInterfaceDto> getDeviceInterfacesById(Long deviceId) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    return getStoredOrRefreshInterfaces(entity);
  }

  @Override
  @Transactional
  public List<DeviceInterfaceDto> refreshDeviceInterfacesById(Long deviceId) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    return refreshAndStoreInterfaces(entity);
  }

  @Override
  @Transactional
  public MonitoringDetailsDto getDeviceMonitoringDetails(String ip) {
    MonitoredDeviceEntity entity = monitoredDeviceRepository.findFirstByIpOrderByUpdatedAtDesc(ip)
        .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено на мониторинге."));
    return getStoredOrRefreshDetails(entity);
  }

  @Override
  @Transactional
  public MonitoringDetailsDto getDeviceMonitoringDetailsById(Long deviceId) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    return getStoredOrRefreshDetails(entity);
  }

  @Override
  @Transactional
  public MonitoringDetailsDto refreshDeviceMonitoringDetails(String ip, boolean liveMode) {
    MonitoredDeviceEntity entity = monitoredDeviceRepository.findFirstByIpOrderByUpdatedAtDesc(ip)
        .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено на мониторинге."));
    return refreshAndStoreDetails(entity, liveMode ? DETAILS_SOURCE_LIVE_REFRESH : DETAILS_SOURCE_MANUAL_REFRESH, liveMode);
  }

  @Override
  @Transactional
  public MonitoringDetailsDto refreshDeviceMonitoringDetailsById(Long deviceId, boolean liveMode) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    return refreshAndStoreDetails(entity, liveMode ? DETAILS_SOURCE_LIVE_REFRESH : DETAILS_SOURCE_MANUAL_REFRESH, liveMode);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MetricValueDto> getMetricsWithUnits(
      String ip, OffsetDateTime from, OffsetDateTime to, String metricName
  ) {
    MonitoredDeviceEntity entity = monitoredDeviceRepository.findFirstByIpOrderByUpdatedAtDesc(ip)
        .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено на мониторинге."));
    return queryMetrics(entity, from, to, metricName);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MetricValueDto> getMetricsWithUnitsById(
      Long deviceId, OffsetDateTime from, OffsetDateTime to, String metricName
  ) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    return queryMetrics(entity, from, to, metricName);
  }

  @Override
  @Transactional(readOnly = true)
  public DeviceMetricsHistoryResponseDto getMetricsHistoryById(
      Long deviceId,
      OffsetDateTime from,
      OffsetDateTime to,
      String metricName,
      String q,
      Integer panelsOffset,
      Integer panelsLimit,
      Integer maxPoints
  ) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, toResult(entity));
    Map<String, List<DiscoveryInstanceRuntime>> activeInstances =
        runtimeStateService.loadActiveDiscoveryInstances(entity);

    boolean singleMetric = metricName != null && !metricName.isBlank();
    Set<String> metricNames = singleMetric
        ? new LinkedHashSet<>(List.of(metricName))
        : new LinkedHashSet<>(metricsHistoryService.listMetricNamesInRange(entity.getIp(), from, to));
    if (metricNames.isEmpty()) {
      return new DeviceMetricsHistoryResponseDto(List.of(), 0);
    }

    Map<String, String> displayNamesByMetric =
        buildMetricDisplayNamesForKeys(entity, template, metricNames);

    List<MetricChartPanelDto> layout = metricChartLayoutBuilder.build(
        template,
        activeInstances,
        metricNames,
        displayNamesByMetric
    );
    layout = filterChartPanelsByQuery(layout, q, displayNamesByMetric);
    int total = layout.size();
    int fromIdx = panelsOffset == null ? 0 : Math.max(0, panelsOffset);
    if (fromIdx >= total) {
      return new DeviceMetricsHistoryResponseDto(List.of(), total);
    }
    boolean unlimited = panelsLimit == null || panelsLimit <= 0;
    int toIdx;
    if (unlimited) {
      toIdx = total;
    } else {
      int lim = Math.min(panelsLimit, MAX_CHART_PANEL_PAGE);
      toIdx = Math.min(fromIdx + lim, total);
    }
    List<MetricChartPanelDto> page = layout.subList(fromIdx, toIdx);

    Set<String> pageMetricNames = new LinkedHashSet<>();
    for (MetricChartPanelDto panel : page) {
      pageMetricNames.addAll(metricNamesForChartPanel(panel));
    }
    List<MetricValueDto> points = pageMetricNames.isEmpty()
        ? List.of()
        : queryMetrics(entity, from, to, pageMetricNames, maxPoints);

    Map<String, String> unitByMetric = loadUnitLabelsByItemKey(entity);
    Map<String, UnitScalingService.SeriesScale> seriesScaleByMetric =
        resolveSeriesScaleByMetric(points, unitByMetric);
    Map<String, List<MetricValueDto>> pointsByMetric = points.stream()
        .filter(point -> point.metricName() != null && !point.metricName().isBlank())
        .collect(java.util.stream.Collectors.groupingBy(
            MetricValueDto::metricName,
            java.util.LinkedHashMap::new,
            java.util.stream.Collectors.toList()
        ));
    List<MetricChartThresholdDto> deviceThresholds = metricChartThresholdBuilder.build(
        entity,
        template,
        activeInstances,
        to,
        unitByMetric,
        seriesScaleByMetric,
        new MetricChartThresholdBuilder.ChartThresholdBuildContext(from, to, pointsByMetric)
    );

    List<MetricChartPanelDto> withPoints = attachMetricPointsToPanels(page, points, deviceThresholds);
    return new DeviceMetricsHistoryResponseDto(withPoints, total);
  }

  @Override
  @Transactional(readOnly = true)
  public CompactMetricsHistoryResponseDto getMetricsHistoryCompactById(
      Long deviceId,
      OffsetDateTime from,
      OffsetDateTime to,
      String metricName,
      String q,
      Integer panelsOffset,
      Integer panelsLimit,
      Integer maxPoints
  ) {
    DeviceMetricsHistoryResponseDto rich = getMetricsHistoryById(
        deviceId, from, to, metricName, q, panelsOffset, panelsLimit, maxPoints);
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, toResult(entity));
    LinkedHashSet<String> metricNames = new LinkedHashSet<>();
    for (MetricChartPanelDto panel : rich.chartPanels()) {
      metricNames.addAll(metricNamesForChartPanel(panel));
    }
    Map<String, ValueMapSeriesMeta> metaByMetric =
        ValueMapSeriesResolver.resolveAll(template, metricNames);
    return ChartCompactMapper.toCompactResponse(rich, metaByMetric);
  }

  private Map<String, UnitScalingService.SeriesScale> resolveSeriesScaleByMetric(
      List<MetricValueDto> points,
      Map<String, String> unitByMetric
  ) {
    if (points == null || points.isEmpty()) {
      return Map.of();
    }
    Map<String, UnitScalingService.SeriesScale> scaleByMetric = new LinkedHashMap<>();
    Map<String, List<MetricValueDto>> grouped = points.stream()
        .filter(point -> point.metricName() != null && !point.metricName().isBlank())
        .collect(java.util.stream.Collectors.groupingBy(
            MetricValueDto::metricName,
            LinkedHashMap::new,
            java.util.stream.Collectors.toList()
        ));
    for (var entry : grouped.entrySet()) {
      String metricName = entry.getKey();
      List<MetricValueDto> metricPoints = entry.getValue();
      String unit = metricPoints.stream()
          .map(MetricValueDto::unit)
          .filter(value -> value != null && !value.isBlank())
          .findFirst()
          .orElse(unitByMetric.get(metricName));
      double maxAbs = metricPoints.stream()
          .mapToDouble(MetricValueDto::metricValue)
          .filter(Double::isFinite)
          .map(Math::abs)
          .max()
          .orElse(0d);
      scaleByMetric.put(metricName, unitScalingService.resolveSeriesScale(unit, maxAbs));
    }
    return scaleByMetric;
  }

  private static List<MetricChartPanelDto> attachMetricPointsToPanels(
      List<MetricChartPanelDto> layout,
      List<MetricValueDto> allPoints,
      List<MetricChartThresholdDto> deviceThresholds
  ) {
    if (layout == null || layout.isEmpty()) {
      return List.of();
    }
    List<MetricChartPanelDto> out = new ArrayList<>(layout.size());
    for (MetricChartPanelDto panel : layout) {
      Set<String> names = metricNamesForChartPanel(panel);
      List<MetricValueDto> panelPoints = allPoints.stream()
          .filter(p -> p.metricName() != null && names.contains(p.metricName()))
          .toList();
      List<MetricChartThresholdDto> panelThresholds = MetricChartThresholdBuilder.forPanel(deviceThresholds, names);
      out.add(new MetricChartPanelDto(
          panel.panelKey(),
          panel.title(),
          panel.graphType(),
          panel.metricNames(),
          panel.rightAxisMetricNames(),
          panelPoints,
          panelThresholds
      ));
    }
    return out;
  }

  private static List<MetricChartPanelDto> filterChartPanelsByQuery(
      List<MetricChartPanelDto> layout,
      String q,
      Map<String, String> displayNamesByMetric
  ) {
    String qLow = q == null ? "" : q.trim().toLowerCase();
    if (qLow.isEmpty()) {
      return layout;
    }
    return layout.stream()
        .filter(panel -> panelMatchesChartNameQuery(panel, qLow, displayNamesByMetric))
        .toList();
  }

  private static boolean panelMatchesChartNameQuery(
      MetricChartPanelDto panel,
      String qLow,
      Map<String, String> displayNamesByMetric
  ) {
    LinkedHashSet<String> candidates = new LinkedHashSet<>();
    if (panel.title() != null && !panel.title().isBlank()) {
      candidates.add(panel.title().trim());
    }
    if (panel.panelKey() != null && !panel.panelKey().isBlank()) {
      String key = panel.panelKey().trim();
      candidates.add(key);
      candidates.add(key.toUpperCase());
    }
    collectMetricNameCandidates(candidates, panel.metricNames(), displayNamesByMetric);
    collectMetricNameCandidates(candidates, panel.rightAxisMetricNames(), displayNamesByMetric);
    return candidates.stream().anyMatch(value -> value.toLowerCase().contains(qLow));
  }

  private static void collectMetricNameCandidates(
      Set<String> candidates,
      List<String> metricNames,
      Map<String, String> displayNamesByMetric
  ) {
    if (metricNames == null) {
      return;
    }
    for (String name : metricNames) {
      if (name == null || name.isBlank()) {
        continue;
      }
      String trimmed = name.trim();
      candidates.add(trimmed);
      String display = displayNamesByMetric.get(trimmed);
      if (display != null && !display.isBlank()) {
        candidates.add(display.trim());
      }
    }
  }

  private static Set<String> metricNamesForChartPanel(MetricChartPanelDto panel) {
    LinkedHashSet<String> set = new LinkedHashSet<>();
    if (panel.metricNames() != null) {
      for (String name : panel.metricNames()) {
        if (name != null && !name.isBlank()) {
          set.add(name);
        }
      }
    }
    if (panel.rightAxisMetricNames() != null) {
      for (String name : panel.rightAxisMetricNames()) {
        if (name != null && !name.isBlank()) {
          set.add(name);
        }
      }
    }
    return set;
  }

  @Override
  @Transactional(readOnly = true)
  public List<MetricValueDto> getLatestMetricsWithUnits(String ip, String metricName) {
    MonitoredDeviceEntity entity = monitoredDeviceRepository.findFirstByIpOrderByUpdatedAtDesc(ip)
        .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено на мониторинге."));
    return queryLatestMetrics(entity, metricName);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MetricValueDto> getLatestMetricsWithUnitsById(Long deviceId, String metricName) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    return queryLatestMetrics(entity, metricName);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MonitoringMetricsBatchSeriesDto> getMetricsWithUnitsBatch(MonitoringMetricsBatchRequest request) {
    if (request == null || request.series() == null || request.series().isEmpty()) {
      throw new IllegalArgumentException("Нужно передать хотя бы один ряд в поле series.");
    }
    if (request.series().size() > MAX_METRICS_BATCH_SERIES) {
      throw new IllegalArgumentException("Максимум рядов в batch-запросе: " + MAX_METRICS_BATCH_SERIES + ".");
    }
    return request.series().stream()
        .map(row -> toBatchSeriesDto(row, request.from(), request.to(), request.maxPoints()))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<CompactMetricsBatchSeriesDto> getMetricsWithUnitsBatchCompact(MonitoringMetricsBatchRequest request) {
    List<MonitoringMetricsBatchSeriesDto> rich = getMetricsWithUnitsBatch(request);
    Map<Long, ResolvedMonitoringTemplate> templateByDevice = new LinkedHashMap<>();
    return ChartCompactMapper.toCompactBatch(rich, series -> {
      if (series.deviceId() == null) {
        return null;
      }
      ResolvedMonitoringTemplate template = templateByDevice.computeIfAbsent(
          series.deviceId(),
          deviceId -> {
            MonitoredDeviceEntity entity = findEntityById(deviceId);
            return resolveTemplateFor(entity, toResult(entity));
          }
      );
      return ValueMapSeriesResolver.resolve(template, series.metricName());
    });
  }

  @Override
  public List<MonitoringTemplateSummaryDto> listMonitoringTemplates() {
    return templateResolver.listTemplates();
  }

  @Override
  public MonitoringTemplateDetailsDto getMonitoringTemplateDetails(String templateId) {
    return templateResolver.describeTemplate(templateId);
  }

  @Override
  public MonitoringTemplateImportPreviewDto previewMonitoringTemplateArchive(
      String originalFilename,
      byte[] archiveBytes
  ) {
    return templateResolver.previewArchive(originalFilename, archiveBytes);
  }

  @Override
  @Transactional
  public synchronized MonitoringTemplateOperationResultDto uploadMonitoringTemplateArchive(
      String originalFilename,
      byte[] archiveBytes,
      String vendor,
      String model,
      String firmware,
      Authentication authentication
  ) {
    String uploadedBy = authentication != null ? authentication.getName() : null;
    int archiveSize = archiveBytes == null ? 0 : archiveBytes.length;
    String vendorTrimmed = vendor == null ? "" : vendor.trim();
    String modelTrimmed = model == null ? "" : model.trim();
    String firmwareTrimmed = firmware == null ? "" : firmware.trim();
    if (vendorTrimmed.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо заполнить поле «Вендор».");
    }
    log.info(
        "Приём файла шаблона мониторинга: originalFilename={}, sizeBytes={}, uploadedBy={}",
        originalFilename,
        archiveSize,
        uploadedBy
    );

    MonitoringTemplateImportPreviewDto preview;
    try {
      preview = templateResolver.previewArchive(originalFilename, archiveBytes);
    } catch (IllegalArgumentException | IllegalStateException exception) {
      log.warn(
          "Файл шаблона не прошёл проверку (originalFilename={}): {}",
          originalFilename,
          exception.getMessage(),
          exception
      );
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }
    UploadedMonitoringTemplatePackage uploadedPackage = templateArchiveReader.readSingleTemplatePackage(originalFilename, archiveBytes);

    log.info(
        "Файл шаблона распознан: templateId={}, templateFile={}, extendsTemplate={}",
        uploadedPackage.templateId(),
        uploadedPackage.templateFileName(),
        uploadedPackage.extendsTemplate()
    );

    boolean templateExists = templateResolver.listTemplates().stream()
        .anyMatch(template -> template.id().equals(uploadedPackage.templateId()));
    if (templateExists) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Шаблон мониторинга с id " + uploadedPackage.templateId() + " уже существует."
      );
    }

    UploadedMonitoringTemplateEntity entity = new UploadedMonitoringTemplateEntity();
    entity.setTemplateId(uploadedPackage.templateId());
    entity.setExtendsTemplate(uploadedPackage.extendsTemplate());
    entity.setVendor(vendorTrimmed);
    entity.setModel(modelTrimmed.isEmpty() ? null : modelTrimmed);
    entity.setModelRegex(modelTrimmed.isEmpty() ? null : ("^" + Pattern.quote(modelTrimmed) + "$"));
    entity.setFirmware(firmwareTrimmed.isEmpty() ? null : firmwareTrimmed);
    entity.setOriginalFilename(firstNonBlank(originalFilename, uploadedPackage.templateFileName()));
    entity.setManifestYaml(uploadedPackage.manifestYaml());
    entity.setTemplateFileName(uploadedPackage.templateFileName());
    entity.setTemplateYaml(uploadedPackage.templateYaml());
    entity.setUploadedBy(firstNonBlank(uploadedBy, null));
    entity.setUploadedAt(OffsetDateTime.now());
    uploadedMonitoringTemplateRepository.save(entity);

    try {
      templateResolver.initialize();
    } catch (IllegalArgumentException | IllegalStateException exception) {
      log.error(
          "Перезагрузка каталога шаблонов после сохранения записи не удалась (templateId={}, templateFile={}): {}",
          uploadedPackage.templateId(),
          uploadedPackage.templateFileName(),
          exception.getMessage(),
          exception
      );
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    log.info("Шаблон мониторинга успешно загружен и применён: templateId={}", uploadedPackage.templateId());
    auditLogService.record(
        authentication,
        AuditCategory.MONITORING_TEMPLATE,
        AuditAction.CREATE,
        "шаблон=" + uploadedPackage.templateId(),
        "Загружен файл: " + firstNonBlank(originalFilename, uploadedPackage.templateFileName())
    );
    return new MonitoringTemplateOperationResultDto(
        "Шаблон " + uploadedPackage.templateId() + " успешно загружен.",
        preview
    );
  }

  @Override
  @Transactional
  public synchronized MonitoringTemplateOperationResultDto deleteMonitoringTemplate(
      String templateId,
      Authentication authentication
  ) {
    UploadedMonitoringTemplateEntity entity = uploadedMonitoringTemplateRepository.findByTemplateId(templateId)
        .orElseGet(() -> {
          boolean existsAsSystem = templateResolver.listTemplates().stream()
              .anyMatch(template -> template.id().equals(templateId));
          if (existsAsSystem) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Системный шаблон нельзя удалить.");
          }
          throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Шаблон мониторинга не найден.");
        });

    if (uploadedMonitoringTemplateRepository.countByExtendsTemplate(templateId) > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Нельзя удалить шаблон, пока от него наследуются другие загруженные шаблоны."
      );
    }
    if (monitoredDeviceRepository.countTemplateUsage(templateId) > 0) {
      throw new ResponseStatusException(
          HttpStatus.CONFLICT,
          "Нельзя удалить шаблон, пока он используется устройствами на мониторинге."
      );
    }

    uploadedMonitoringTemplateRepository.delete(entity);
    try {
      templateResolver.initialize();
    } catch (IllegalArgumentException | IllegalStateException exception) {
      log.error(
          "Перезагрузка каталога шаблонов после удаления не удалась (удалённый templateId={}): {}",
          templateId,
          exception.getMessage(),
          exception
      );
      throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage(), exception);
    }
    auditLogService.record(
        authentication,
        AuditCategory.MONITORING_TEMPLATE,
        AuditAction.DELETE,
        "шаблон=" + templateId,
        null
    );
    return new MonitoringTemplateOperationResultDto("Шаблон " + templateId + " удален.", null);
  }

  @Override
  @Transactional
  public synchronized MonitoringTemplateOperationResultDto updateMonitoringTemplate(
      String templateId,
      MonitoringTemplateUpdateRequest request,
      Authentication authentication
  ) {
    if (request == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Тело запроса обязательно.");
    }
    if (request.priority() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо указать приоритет.");
    }
    if (request.priority() < 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Приоритет не может быть отрицательным.");
    }
    if (request.priority() > 100) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Приоритет не может быть больше 100.");
    }

    MonitoringTemplateSummaryDto existing = templateResolver.listTemplates().stream()
        .filter(template -> template.id().equals(templateId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Шаблон мониторинга не найден."
        ));

    if (existing.source() == MonitoringTemplateSource.SYSTEM) {
      MonitoringTemplatePriorityOverrideEntity override = priorityOverrideRepository
          .findById(templateId)
          .orElseGet(MonitoringTemplatePriorityOverrideEntity::new);
      override.setTemplateId(templateId);
      override.setPriority(request.priority());
      priorityOverrideRepository.save(override);
    } else {
      if (!hasNonBlank(request.vendor())) {
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо заполнить поле «Вендор».");
      }
      UploadedMonitoringTemplateEntity entity = uploadedMonitoringTemplateRepository.findByTemplateId(templateId)
          .orElseThrow(() -> new ResponseStatusException(
              HttpStatus.NOT_FOUND,
              "Шаблон мониторинга не найден."
          ));
      String vendorTrimmed = request.vendor().trim();
      String modelTrimmed = request.model() == null ? "" : request.model().trim();
      String firmwareTrimmed = request.firmware() == null ? "" : request.firmware().trim();
      entity.setVendor(vendorTrimmed);
      entity.setModel(modelTrimmed.isEmpty() ? null : modelTrimmed);
      entity.setModelRegex(modelTrimmed.isEmpty() ? null : ("^" + Pattern.quote(modelTrimmed) + "$"));
      entity.setFirmware(firmwareTrimmed.isEmpty() ? null : firmwareTrimmed);
      entity.setPriority(request.priority());
      uploadedMonitoringTemplateRepository.save(entity);
    }

    try {
      templateResolver.initialize();
    } catch (IllegalArgumentException | IllegalStateException exception) {
      log.error(
          "Перезагрузка каталога шаблонов после обновления не удалась (templateId={}): {}",
          templateId,
          exception.getMessage(),
          exception
      );
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
    }

    auditLogService.record(
        authentication,
        AuditCategory.MONITORING_TEMPLATE,
        AuditAction.UPDATE,
        "шаблон=" + templateId,
        "priority=" + request.priority()
    );
    return new MonitoringTemplateOperationResultDto(
        "Шаблон " + templateId + " обновлён.",
        null
    );
  }

  private static boolean hasNonBlank(String value) {
    return value != null && !value.isBlank();
  }

  @Override
  @Transactional(readOnly = true)
  public List<MonitoringEventDto> getEventsByDeviceId(Long deviceId) {
    MonitoredDeviceEntity device = findEntityById(deviceId);
    List<MonitoringEventEntity> events = monitoringEventRepository.findByDevice_IdOrderByBreachStartedAtDesc(deviceId);
    return toEventDtos(events, Map.of(deviceId, device));
  }

  @Override
  @Transactional(readOnly = true)
  public MonitoringEventPageDto listMonitoringEvents(
      MonitoringEventFilter filter, int page, int size
  ) {
    validateMonitoringEventFilterDevices(filter);
    String statusStr = filter.status() != null ? filter.status().name() : null;
    String thresholdLevelStr = filter.thresholdLevel() != null ? filter.thresholdLevel().name() : null;
    String metricNamePattern = toIlikeContainsPattern(filter.metricNameContains());
    String macAddressPattern = toIlikeContainsPattern(filter.macAddressContains());
    String deviceIpPattern = toIlikeContainsPattern(filter.deviceIpContains());
    String deviceNamePattern = toIlikeContainsPattern(filter.deviceNameContains());
    String deviceIdsCsv = toDeviceIdsCsv(filter.deviceIds());
    String deviceTagsCsv = toDeviceTagsCsv(filter.deviceTags());
    int safePage = Math.max(page, 0);
    int pageSize = size <= 0 ? DEFAULT_EVENT_PAGE_SIZE : size;
    pageSize = Math.min(pageSize, MAX_EVENT_PAGE_SIZE);
    Pageable pageable = PageRequest.of(safePage, pageSize);

    Page<MonitoringEventEntity> entityPage = monitoringEventRepository.searchEvents(
        statusStr,
        filter.deviceId(),
        thresholdLevelStr,
        filter.breachStartedFrom(),
        filter.breachStartedTo(),
        filter.normalizedFrom(),
        filter.normalizedTo(),
        filter.minDurationSeconds(),
        filter.maxDurationSeconds(),
        metricNamePattern,
        macAddressPattern,
        deviceIpPattern,
        deviceNamePattern,
        deviceIdsCsv,
        deviceTagsCsv,
        pageable
    );

    List<MonitoringEventDto> content = toEventDtos(entityPage.getContent(), Map.of());

    return new MonitoringEventPageDto(
        content,
        entityPage.getTotalElements(),
        entityPage.getTotalPages(),
        entityPage.getNumber(),
        entityPage.getSize(),
        entityPage.isFirst(),
        entityPage.isLast()
    );
  }

  private List<MonitoringEventDto> toEventDtos(
      List<MonitoringEventEntity> events,
      Map<Long, MonitoredDeviceEntity> knownDevices
  ) {
    if (events == null || events.isEmpty()) {
      return List.of();
    }

    Map<Long, Set<String>> metricNamesByDevice = new LinkedHashMap<>();
    Map<Long, MonitoredDeviceEntity> devicesById = new LinkedHashMap<>();
    if (knownDevices != null && !knownDevices.isEmpty()) {
      devicesById.putAll(knownDevices);
    }

    for (MonitoringEventEntity event : events) {
      if (event == null || event.getMetricName() == null || event.getMetricName().isBlank()) {
        continue;
      }
      MonitoredDeviceEntity eventDevice = event.getDevice();
      Long eventDeviceId = eventDevice != null ? eventDevice.getId() : null;
      if (eventDeviceId == null) {
        continue;
      }
      devicesById.putIfAbsent(eventDeviceId, eventDevice);
      metricNamesByDevice.computeIfAbsent(eventDeviceId, ignored -> new LinkedHashSet<>()).add(event.getMetricName());
    }

    Map<Long, Map<String, String>> displayNamesByDevice = new HashMap<>();
    for (var entry : metricNamesByDevice.entrySet()) {
      Long eventDeviceId = entry.getKey();
      MonitoredDeviceEntity resolvedDevice = devicesById.get(eventDeviceId);
      if (resolvedDevice == null) {
        resolvedDevice = findEntityById(eventDeviceId);
        devicesById.put(eventDeviceId, resolvedDevice);
      }
      String resolvedDeviceIp = resolvedDevice.getIp();
      ResolvedMonitoringTemplate template = resolveTemplateFor(resolvedDevice, toResult(resolvedDevice));
      List<MetricValueDto> stubs = entry.getValue().stream()
          .map(metricName -> new MetricValueDto(null, resolvedDeviceIp, metricName, 0d, null, null))
          .toList();
      displayNamesByDevice.put(eventDeviceId, buildMetricDisplayNames(resolvedDevice, template, stubs));
    }

    return events.stream()
        .map(event -> {
          MonitoredDeviceEntity eventDevice = event.getDevice();
          Long eventDeviceId = eventDevice != null ? eventDevice.getId() : null;
          Map<String, String> displayNames = eventDeviceId == null
              ? Map.of()
              : displayNamesByDevice.getOrDefault(eventDeviceId, Map.of());
          return toEventDto(event, displayNames.get(event.getMetricName()));
        })
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public MonitoringEventLevelSummaryDto summarizeMonitoringEventsByLevel(MonitoringEventFilter filter) {
    validateMonitoringEventFilterDevices(filter);
    String statusStr = filter.status() != null ? filter.status().name() : null;
    String thresholdLevelStr = filter.thresholdLevel() != null ? filter.thresholdLevel().name() : null;
    String metricNamePattern = toIlikeContainsPattern(filter.metricNameContains());
    String macAddressPattern = toIlikeContainsPattern(filter.macAddressContains());
    String deviceIpPattern = toIlikeContainsPattern(filter.deviceIpContains());
    String deviceNamePattern = toIlikeContainsPattern(filter.deviceNameContains());
    String deviceIdsCsv = toDeviceIdsCsv(filter.deviceIds());
    String deviceTagsCsv = toDeviceTagsCsv(filter.deviceTags());
    List<Object[]> rows = monitoringEventRepository.aggregateEventLevelCounts(
        statusStr,
        filter.deviceId(),
        thresholdLevelStr,
        filter.breachStartedFrom(),
        filter.breachStartedTo(),
        filter.normalizedFrom(),
        filter.normalizedTo(),
        filter.minDurationSeconds(),
        filter.maxDurationSeconds(),
        metricNamePattern,
        macAddressPattern,
        deviceIpPattern,
        deviceNamePattern,
        deviceIdsCsv,
        deviceTagsCsv
    );
    if (rows == null || rows.isEmpty()) {
      return new MonitoringEventLevelSummaryDto(0, 0, 0, 0, 0, 0);
    }
    Object[] row = rows.get(0);
    if (row == null || row.length < 6) {
      return new MonitoringEventLevelSummaryDto(0, 0, 0, 0, 0, 0);
    }
    return new MonitoringEventLevelSummaryDto(
        toCount(row[0]),
        toCount(row[1]),
        toCount(row[2]),
        toCount(row[3]),
        toCount(row[4]),
        toCount(row[5])
    );
  }

  private static long toCount(Object value) {
    if (value == null) {
      return 0L;
    }
    return ((Number) value).longValue();
  }

  @Override
  @Transactional(readOnly = true)
  public MonitoringItemStatePageDto getItemStatePage(Long deviceId, String q, int page, int size) {
    List<MonitoringItemStateDto> all = buildItemStateDtos(deviceId);
    String qLow = q == null ? "" : q.trim().toLowerCase();
    List<MonitoringItemStateDto> filtered = all.stream()
        .filter(dto -> {
          if (qLow.isEmpty()) {
            return true;
          }
          boolean keyMatch = dto.itemKey() != null && dto.itemKey().toLowerCase().contains(qLow);
          boolean nameMatch = dto.itemDisplayName() != null
              && dto.itemDisplayName().toLowerCase().contains(qLow);
          return keyMatch || nameMatch;
        })
        .sorted(java.util.Comparator.comparing(MonitoringItemStateDto::itemKey)
            .thenComparing(
                MonitoringItemStateDto::instanceKey,
                java.util.Comparator.nullsFirst(String::compareToIgnoreCase)))
        .toList();
    return toItemStatePage(filtered, page, size);
  }

  private List<MonitoringItemStateDto> buildItemStateDtos(Long deviceId) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    List<com.networkscanner.backend.monitoring.dto.ItemStateSnapshot> snapshots =
        runtimeStateService.loadItemStateList(entity);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, toResult(entity));
    Map<String, List<DiscoveryInstanceRuntime>> activeInstances =
        runtimeStateService.loadActiveDiscoveryInstances(entity);
    Set<String> itemKeys = snapshots.stream()
        .map(com.networkscanner.backend.monitoring.dto.ItemStateSnapshot::itemKey)
        .filter(key -> key != null && !key.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    Map<String, String> displayNames = buildMetricDisplayNamesForKeys(entity, template, itemKeys);
    Map<String, String> unitByMetric = snapshots.stream()
        .filter(snapshot -> snapshot.itemKey() != null && !snapshot.itemKey().isBlank())
        .filter(snapshot -> snapshot.unitLabel() != null && !snapshot.unitLabel().isBlank())
        .collect(java.util.stream.Collectors.toMap(
            com.networkscanner.backend.monitoring.dto.ItemStateSnapshot::itemKey,
            com.networkscanner.backend.monitoring.dto.ItemStateSnapshot::unitLabel,
            (left, right) -> left,
            LinkedHashMap::new
        ));
    OffsetDateTime now = OffsetDateTime.now();
    List<MetricChartThresholdDto> thresholds = metricChartThresholdBuilder.build(
        entity,
        template,
        activeInstances,
        now,
        unitByMetric,
        Map.of()
    );
    Map<String, List<MetricChartThresholdDto>> thresholdsByMetricInstance =
        metricChartThresholdBuilder.indexByMetricInstance(thresholds);
    return snapshots.stream()
        .map(snapshot -> toItemStateDto(
            snapshot,
            displayNames.get(snapshot.itemKey()),
            thresholdsByMetricInstance.getOrDefault(
                TriggerEvaluationSupport.metricInstanceKey(
                    snapshot.itemKey(),
                    TriggerEvaluationSupport.blankToEmpty(snapshot.instanceKey())
                ),
                List.of()
            ),
            template
        ))
        .sorted(java.util.Comparator.comparing(MonitoringItemStateDto::itemKey)
            .thenComparing(MonitoringItemStateDto::instanceKey, java.util.Comparator.nullsFirst(String::compareToIgnoreCase)))
        .toList();
  }

  private MonitoringItemStatePageDto toItemStatePage(List<MonitoringItemStateDto> all, int page, int size) {
    int safePage = Math.max(page, 0);
    int pageSize = size <= 0 ? DEFAULT_ITEM_STATE_PAGE_SIZE : size;
    pageSize = Math.min(pageSize, MAX_ITEM_STATE_PAGE_SIZE);
    int totalElements = all.size();
    int totalPages = totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
    if (totalElements == 0) {
      return new MonitoringItemStatePageDto(List.of(), 0, 0, safePage, pageSize, true, true);
    }
    if (safePage >= totalPages) {
      safePage = Math.max(totalPages - 1, 0);
    }
    int fromIndex = safePage * pageSize;
    int toIndex = Math.min(fromIndex + pageSize, totalElements);
    List<MonitoringItemStateDto> content = all.subList(fromIndex, toIndex);
    return new MonitoringItemStatePageDto(
        content,
        totalElements,
        totalPages,
        safePage,
        pageSize,
        safePage == 0,
        safePage >= totalPages - 1
    );
  }

  @Override
  @Transactional(readOnly = true)
  public List<MonitoringDiscoveryInstanceDto> getDiscoveryStateByDeviceId(Long deviceId) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    return runtimeStateService.loadActiveDiscoveryInstances(entity).values().stream()
        .flatMap(List::stream)
        .map(instance -> new MonitoringDiscoveryInstanceDto(
            instance.discoveryRuleKey(),
            instance.instanceKey(),
            instance.macros(),
            instance.lastDiscoveredAt(),
            instance.expiresAt()
        ))
        .sorted(java.util.Comparator.comparing(MonitoringDiscoveryInstanceDto::discoveryRuleKey)
            .thenComparing(MonitoringDiscoveryInstanceDto::instanceKey))
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public List<MonitoringDeviceItemDto> getDeviceItemsByDeviceId(Long deviceId) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, toResult(entity));
    return monitoredDeviceItemService.listDeviceItems(entity, template);
  }

  @Override
  @Transactional
  public List<MonitoringDeviceItemDto> updateDeviceItemsByDeviceId(
      Long deviceId,
      MonitoringDeviceItemsUpdateRequest request,
      Authentication authentication
  ) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, toResult(entity));
    List<MonitoringDeviceItemDto> result =
        monitoredDeviceItemService.replaceActiveItems(entity, template, request.activeItems());
    auditLogService.record(
        authentication,
        AuditCategory.MONITORING_DEVICE,
        AuditAction.UPDATE,
        "deviceId=" + deviceId + ", ip=" + entity.getIp(),
        "Обновлён набор активных item мониторинга"
    );
    return result;
  }

  @Override
  @Transactional
  public void deactivateDeviceItemByDeviceId(
      Long deviceId,
      String itemUuid,
      String instanceKey,
      Authentication authentication
  ) {
    MonitoredDeviceEntity entity = findEntityById(deviceId);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, toResult(entity));
    monitoredDeviceItemService.deactivateItem(entity, template, itemUuid, instanceKey);
    String detail = instanceKey == null || instanceKey.isBlank()
        ? "Снят item " + itemUuid
        : "Снят item " + itemUuid + ", instance=" + instanceKey;
    auditLogService.record(
        authentication,
        AuditCategory.MONITORING_DEVICE,
        AuditAction.UPDATE,
        "deviceId=" + deviceId + ", ip=" + entity.getIp(),
        detail
    );
  }

  @Override
  public synchronized List<DeviceScanResult> matchScanResults(List<DeviceScanResult> scanned) {
    return scanned.stream().map(scan -> {
      Optional<MonitoredDeviceEntity> existing = resolveExistingEntity(scan);
      return existing.map(entity -> scan.withMonitoredDeviceId(entity.getId())).orElse(scan);
    }).toList();
  }

  // ---- internal helpers ----

  private MonitoredDeviceEntity findEntityById(Long deviceId) {
    return monitoredDeviceRepository.findById(deviceId)
        .orElseThrow(() -> new IllegalArgumentException("Устройство не найдено на мониторинге."));
  }

  private List<DeviceInterfaceDto> readInterfaces(MonitoredDeviceEntity entity) {
    DeviceScanResult device = toResult(entity);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, device);
    return scanService.readInterfaces(entity.getIp(), template);
  }

  private List<DeviceInterfaceDto> getStoredOrRefreshInterfaces(MonitoredDeviceEntity entity) {
    List<DeviceInterfaceDto> stored = readStoredInterfaces(entity.getId());
    return stored.isEmpty() ? refreshAndStoreInterfaces(entity) : stored;
  }

  private List<DeviceInterfaceDto> refreshAndStoreInterfaces(MonitoredDeviceEntity entity) {
    List<DeviceInterfaceDto> fresh = readInterfaces(entity);
    Map<String, MonitoredDeviceInterfaceEntity> storedByName = monitoredDeviceInterfaceRepository
        .findByDevice_IdOrderByNameAsc(entity.getId()).stream()
        .collect(java.util.stream.Collectors.toMap(
            MonitoredDeviceInterfaceEntity::getName,
            interfaceEntity -> interfaceEntity,
            (left, right) -> left,
            LinkedHashMap::new
        ));

    OffsetDateTime now = OffsetDateTime.now();
    Map<String, DeviceInterfaceDto> freshByName = new LinkedHashMap<>();
    for (DeviceInterfaceDto dto : fresh) {
      freshByName.put(dto.name(), dto);
    }

    List<MonitoredDeviceInterfaceEntity> toSave = new ArrayList<>();
    // SNMP/template discovery may emit the same ifName more than once; DB has uq (device_id, name).
    for (DeviceInterfaceDto dto : freshByName.values()) {
      MonitoredDeviceInterfaceEntity existing = storedByName.get(dto.name());
      toSave.add(toInterfaceEntity(entity, existing, dto, "Нет", now));
    }

    for (MonitoredDeviceInterfaceEntity existing : storedByName.values()) {
      if (freshByName.containsKey(existing.getName())) {
        continue;
      }
      DeviceInterfaceDto lostDto = new DeviceInterfaceDto(
          existing.getName(),
          existing.getDescription(),
          existing.getAdminStatus(),
          "DOWN",
          "Да",
          existing.getNominalSpeed(),
          "0 b/s",
          existing.getPurpose(),
          existing.getMode(),
          existing.getKind()
      );
      toSave.add(toInterfaceEntity(entity, existing, lostDto, "Да", now));
    }

    monitoredDeviceInterfaceRepository.saveAll(toSave);
    return toSave.stream()
        .sorted(java.util.Comparator.comparing(MonitoredDeviceInterfaceEntity::getName, String.CASE_INSENSITIVE_ORDER))
        .map(this::toInterfaceDto)
        .toList();
  }

  private List<DeviceInterfaceDto> readStoredInterfaces(Long deviceId) {
    return monitoredDeviceInterfaceRepository.findByDevice_IdOrderByNameAsc(deviceId).stream()
        .map(this::toInterfaceDto)
        .toList();
  }

  private MonitoringDetailsDto getStoredOrRefreshDetails(MonitoredDeviceEntity entity) {
    MonitoringDetailsDto base = monitoringTelemetrySnapshotRepository.findByDevice_Id(entity.getId())
        .map(this::toDetailsDto)
        .orElseGet(() -> refreshAndStoreDetails(entity, DETAILS_SOURCE_BOOTSTRAP, false));
    return enrichDetailsFromItemState(entity, base);
  }

  private MonitoringDetailsDto refreshAndStoreDetails(
      MonitoredDeviceEntity entity,
      String source,
      boolean liveMode
  ) {
    MonitoringDetailsDto details = readDetails(entity, source, liveMode);
    MonitoringTelemetrySnapshotEntity target = monitoringTelemetrySnapshotRepository.findByDevice_Id(entity.getId())
        .orElseGet(MonitoringTelemetrySnapshotEntity::new);
    target.setDevice(entity);
    target.setCpuCurrent(details.cpu().current());
    target.setCpuAverage(details.cpu().average());
    target.setCpuPeak(details.cpu().peak());
    target.setCpuCurrentItemName(details.cpu().currentItemName());
    target.setCpuAverageItemName(details.cpu().averageItemName());
    target.setCpuPeakItemName(details.cpu().peakItemName());
    target.setRamUsedPercent(details.ramUsedPercent());
    target.setRomUsedPercent(details.romUsedPercent());
    target.setUptime(details.uptime());
    target.setDescription(details.description());
    target.setAdminContact(details.adminContact());
    target.setHardwareVersion(details.hardwareVersion());
    target.setLocation(details.location());
    target.setAddedAt(details.addedAt());
    target.setBootVersion(details.bootVersion());
    target.setCollectedAt(details.collectedAt());
    target.setSource(details.source());
    target.setLiveMode(details.liveMode());
    target.setUpdatedAt(OffsetDateTime.now());
    monitoringTelemetrySnapshotRepository.save(target);
    return details;
  }

  private MonitoringDetailsDto readDetails(MonitoredDeviceEntity entity, String source, boolean liveMode) {
    DeviceScanResult device = toResult(entity);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, device);
    MonitoringDetailsDto snmpDetails = scanService.readMonitoringDetails(entity.getIp(), template);
    OffsetDateTime collectedAt = OffsetDateTime.now();
    MonitoringDetailsDto built = new MonitoringDetailsDto(
        snmpDetails.cpu(),
        snmpDetails.ramUsedPercent(),
        snmpDetails.romUsedPercent(),
        snmpDetails.uptime(),
        "-".equals(snmpDetails.description()) ? firstNonBlank(device.name(), "-") : snmpDetails.description(),
        snmpDetails.adminContact(),
        snmpDetails.hardwareVersion(),
        "-".equals(snmpDetails.location()) ? firstNonBlank(device.group(), "-") : snmpDetails.location(),
        formatCreatedAt(entity.getCreatedAt()),
        "-".equals(snmpDetails.bootVersion()) ? firstNonBlank(device.firmwareVersion(), "-") : snmpDetails.bootVersion(),
        collectedAt,
        source,
        liveMode
    );
    return enrichDetailsFromItemState(entity, built);
  }

  /**
   * Fills gaps in CPU/RAM/ROM telemetry from {@code monitoring_item_state} when direct SNMP snapshot has no values
   * (e.g. Cisco IOS walk/LLD items collected by the metric collector).
   */
  private MonitoringDetailsDto enrichDetailsFromItemState(MonitoredDeviceEntity entity, MonitoringDetailsDto base) {
    if (base == null || entity == null) {
      return base;
    }
    if (!telemetryNeedsItemStateEnrichment(base)) {
      return base;
    }

    List<ItemStateSnapshot> itemState = runtimeStateService.loadItemStateList(entity);
    if (itemState.isEmpty()) {
      return base;
    }

    Map<String, Double> values = new LinkedHashMap<>();
    for (ItemStateSnapshot row : itemState) {
      if (row.itemKey() == null || row.itemKey().isBlank()) {
        continue;
      }
      Double numeric = row.numericValue();
      if (numeric == null || !Double.isFinite(numeric)) {
        continue;
      }
      values.put(row.itemKey(), numeric);
    }
    if (values.isEmpty()) {
      return base;
    }

    DeviceScanResult device = toResult(entity);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, device);
    Map<String, ZabbixItemRuntime> definitions =
        template != null && template.items() != null ? template.items() : Map.of();
    ItemStateTelemetrySnapshot fromItems = scanService.resolveTelemetryFromItemValues(values, definitions);
    if (fromItems == null) {
      return base;
    }

    MonitoringMetricDto mergedCpu = mergeCpuMetric(base.cpu(), fromItems.cpu());
    mergedCpu = applyItemStateDisplayNames(entity, template, values.keySet(), mergedCpu);
    Integer mergedRam = base.ramUsedPercent() != null ? base.ramUsedPercent() : fromItems.ramUsedPercent();
    Integer mergedRom = base.romUsedPercent() != null ? base.romUsedPercent() : fromItems.romUsedPercent();

    if (mergedCpu == base.cpu() && mergedRam == base.ramUsedPercent() && mergedRom == base.romUsedPercent()) {
      return base;
    }

    return new MonitoringDetailsDto(
        mergedCpu,
        mergedRam,
        mergedRom,
        base.uptime(),
        base.description(),
        base.adminContact(),
        base.hardwareVersion(),
        base.location(),
        base.addedAt(),
        base.bootVersion(),
        base.collectedAt(),
        base.source(),
        base.liveMode()
    );
  }

  private static boolean telemetryNeedsItemStateEnrichment(MonitoringDetailsDto details) {
    if (details == null) {
      return false;
    }
    MonitoringMetricDto cpu = details.cpu();
    boolean cpuMissing = cpu == null
        || (cpu.current() == null && cpu.average() == null && cpu.peak() == null);
    return cpuMissing || details.ramUsedPercent() == null || details.romUsedPercent() == null;
  }

  private static MonitoringMetricDto mergeCpuMetric(MonitoringMetricDto snmp, MonitoringMetricDto fromItems) {
    if (fromItems == null) {
      return snmp;
    }
    if (snmp == null) {
      return fromItems;
    }
    return new MonitoringMetricDto(
        snmp.current() != null ? snmp.current() : fromItems.current(),
        snmp.average() != null ? snmp.average() : fromItems.average(),
        snmp.peak() != null ? snmp.peak() : fromItems.peak(),
        coalesceNonBlank(snmp.currentItemName(), fromItems.currentItemName()),
        coalesceNonBlank(snmp.averageItemName(), fromItems.averageItemName()),
        coalesceNonBlank(snmp.peakItemName(), fromItems.peakItemName())
    );
  }

  private static String coalesceNonBlank(String primary, String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary;
    }
    if (fallback != null && !fallback.isBlank()) {
      return fallback;
    }
    return null;
  }

  private MonitoringMetricDto applyItemStateDisplayNames(
      MonitoredDeviceEntity entity,
      ResolvedMonitoringTemplate template,
      Set<String> itemKeys,
      MonitoringMetricDto cpu
  ) {
    if (cpu == null || itemKeys == null || itemKeys.isEmpty()) {
      return cpu;
    }
    Map<String, String> displayNames = buildMetricDisplayNamesForKeys(entity, template, itemKeys);
    if (displayNames.isEmpty()) {
      return cpu;
    }
    return new MonitoringMetricDto(
        cpu.current(),
        cpu.average(),
        cpu.peak(),
        resolveTelemetryItemLabel(cpu.currentItemName(), displayNames),
        resolveTelemetryItemLabel(cpu.averageItemName(), displayNames),
        resolveTelemetryItemLabel(cpu.peakItemName(), displayNames)
    );
  }

  private static String resolveTelemetryItemLabel(String currentLabel, Map<String, String> displayNames) {
    if (currentLabel == null || currentLabel.isBlank()) {
      return currentLabel;
    }
    String mapped = displayNames.get(currentLabel);
    if (mapped != null && !mapped.isBlank()) {
      return mapped;
    }
    return currentLabel;
  }

  private MonitoringDetailsDto toDetailsDto(MonitoringTelemetrySnapshotEntity snapshot) {
    return new MonitoringDetailsDto(
        new com.networkscanner.backend.monitoring.dto.MonitoringMetricDto(
            snapshot.getCpuCurrent(),
            snapshot.getCpuAverage(),
            snapshot.getCpuPeak(),
            snapshot.getCpuCurrentItemName(),
            snapshot.getCpuAverageItemName(),
            snapshot.getCpuPeakItemName()
        ),
        snapshot.getRamUsedPercent(),
        snapshot.getRomUsedPercent(),
        snapshot.getUptime(),
        snapshot.getDescription(),
        snapshot.getAdminContact(),
        snapshot.getHardwareVersion(),
        snapshot.getLocation(),
        snapshot.getAddedAt(),
        snapshot.getBootVersion(),
        snapshot.getCollectedAt(),
        snapshot.getSource(),
        snapshot.isLiveMode()
    );
  }

  private List<MetricValueDto> queryMetrics(
      MonitoredDeviceEntity entity, OffsetDateTime from, OffsetDateTime to, String metricName
  ) {
    List<MetricValueDto> rawValues = metricsHistoryService.queryMetricValues(entity.getIp(), from, to, metricName);
    return enrichAndScale(entity, rawValues);
  }

  private List<MetricValueDto> queryMetrics(
      MonitoredDeviceEntity entity,
      OffsetDateTime from,
      OffsetDateTime to,
      java.util.Collection<String> metricNames,
      Integer maxPoints
  ) {
    List<MetricValueDto> rawValues =
        metricsHistoryService.queryMetricValues(entity.getIp(), from, to, metricNames, maxPoints);
    return enrichAndScale(entity, rawValues);
  }

  private List<MetricValueDto> enrichAndScale(MonitoredDeviceEntity entity, List<MetricValueDto> rawValues) {
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, toResult(entity));
    Map<String, String> displayNames = buildMetricDisplayNames(entity, template, rawValues);
    return applyMetricScaling(enrichMetricUnits(entity, template, rawValues, displayNames));
  }

  private List<MetricValueDto> queryLatestMetrics(MonitoredDeviceEntity entity, String metricName) {
    List<MetricValueDto> rawValues = metricsHistoryService.queryLatestMetricValues(entity.getIp(), metricName);
    ResolvedMonitoringTemplate template = resolveTemplateFor(entity, toResult(entity));
    Map<String, String> displayNames = buildMetricDisplayNames(entity, template, rawValues);
    return applyMetricScaling(enrichMetricUnits(entity, template, rawValues, displayNames));
  }

  private List<MetricValueDto> enrichMetricUnits(
      MonitoredDeviceEntity entity,
      ResolvedMonitoringTemplate template,
      List<MetricValueDto> values,
      Map<String, String> displayNames
  ) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    Map<String, String> unitByItemKey = loadUnitLabelsByItemKey(entity);
    Map<String, MetricDefinition> metrics =
        template != null && template.metrics() != null ? template.metrics() : Map.of();
    Map<String, UnitDefinition> units =
        template != null && template.units() != null ? template.units() : Map.of();
    return values.stream()
        .map(dto -> new MetricValueDto(
            dto.recordedAt(),
            dto.deviceIp(),
            dto.metricName(),
            dto.metricValue(),
            resolveMetricUnit(dto.metricName(), dto.unit(), metrics, units, unitByItemKey),
            displayNames.get(dto.metricName())))
        .toList();
  }

  private Map<String, String> loadUnitLabelsByItemKey(MonitoredDeviceEntity entity) {
    return runtimeStateService.loadItemStateList(entity).stream()
        .filter(snapshot -> snapshot.itemKey() != null && !snapshot.itemKey().isBlank())
        .filter(snapshot -> snapshot.unitLabel() != null && !snapshot.unitLabel().isBlank())
        .collect(java.util.stream.Collectors.toMap(
            ItemStateSnapshot::itemKey,
            ItemStateSnapshot::unitLabel,
            (left, right) -> left,
            LinkedHashMap::new
        ));
  }

  private String resolveMetricUnit(
      String metricName,
      String unitFromHistory,
      Map<String, MetricDefinition> metrics,
      Map<String, UnitDefinition> units,
      Map<String, String> unitByItemKey
  ) {
    if (unitFromHistory != null && !unitFromHistory.isBlank()) {
      return unitFromHistory;
    }
    String fromTemplate = resolveUnitLabel(metricName, metrics, units);
    if (fromTemplate != null && !fromTemplate.isBlank()) {
      return fromTemplate;
    }
    if (metricName != null && unitByItemKey.containsKey(metricName)) {
      return unitByItemKey.get(metricName);
    }
    return unitFromHistory;
  }

  private List<MetricValueDto> applyMetricScaling(List<MetricValueDto> values) {
    if (values == null || values.isEmpty()) {
      return List.of();
    }
    Map<String, UnitScalingService.SeriesScale> scaleByMetric = new LinkedHashMap<>();
    Map<String, List<MetricValueDto>> grouped = values.stream()
        .collect(java.util.stream.Collectors.groupingBy(
            MetricValueDto::metricName,
            LinkedHashMap::new,
            java.util.stream.Collectors.toList()
        ));
    for (var entry : grouped.entrySet()) {
      String metricName = entry.getKey();
      List<MetricValueDto> points = entry.getValue();
      if (metricName == null || metricName.isBlank() || points == null || points.isEmpty()) {
        continue;
      }
      String unit = points.stream()
          .map(MetricValueDto::unit)
          .filter(value -> value != null && !value.isBlank())
          .findFirst()
          .orElse(null);
      double maxAbs = points.stream()
          .mapToDouble(MetricValueDto::metricValue)
          .filter(Double::isFinite)
          .map(Math::abs)
          .max()
          .orElse(0d);
      scaleByMetric.put(metricName, unitScalingService.resolveSeriesScale(unit, maxAbs));
    }
    return values.stream()
        .map(value -> {
          UnitScalingService.SeriesScale scale = scaleByMetric.get(value.metricName());
          UnitScalingService.ScalingResult scaled = unitScalingService.applySeriesScale(
              value.metricValue(),
              value.unit(),
              scale
          );
          return new MetricValueDto(
              value.recordedAt(),
              value.deviceIp(),
              value.metricName(),
              value.metricValue(),
              value.unit(),
              value.metricDisplayName(),
              scaled.scaledValue(),
              scaled.scaledUnit(),
              scaled.displayValue()
          );
        })
        .toList();
  }

  private MonitoringMetricsBatchSeriesDto toBatchSeriesDto(
      MonitoringMetricsBatchSeriesRequest request,
      OffsetDateTime from,
      OffsetDateTime to,
      Integer maxPoints
  ) {
    String metricName = request.metricName().strip();
    if (metricName.isEmpty()) {
      throw new IllegalArgumentException("Имя метрики в series.metricName не может быть пустым.");
    }
    MonitoredDeviceEntity entity = findEntityById(request.deviceId());
    List<MetricValueDto> points = queryMetrics(entity, from, to, List.of(metricName), maxPoints);
    return new MonitoringMetricsBatchSeriesDto(request.deviceId(), metricName, points);
  }

  private Map<String, String> buildMetricDisplayNames(
      MonitoredDeviceEntity entity,
      ResolvedMonitoringTemplate template,
      List<MetricValueDto> rawValues
  ) {
    if (rawValues == null || rawValues.isEmpty()) {
      return Map.of();
    }
    Set<String> metricKeys = rawValues.stream()
        .map(MetricValueDto::metricName)
        .filter(value -> value != null && !value.isBlank())
        .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    return buildMetricDisplayNamesForKeys(entity, template, metricKeys);
  }

  private Map<String, String> buildMetricDisplayNamesForKeys(
      MonitoredDeviceEntity entity,
      ResolvedMonitoringTemplate template,
      Set<String> metricKeys
  ) {
    if (template == null || metricKeys == null || metricKeys.isEmpty()) {
      return Map.of();
    }

    Map<String, String> byMetric = new LinkedHashMap<>();
    Map<String, MetricDefinition> metrics = template.metrics() == null ? Map.of() : template.metrics();
    for (String key : metricKeys) {
      MetricDefinition def = metrics.get(key);
      String display = def == null ? null : normalizeDisplay(def.itemDisplayName());
      if (display != null) {
        byMetric.put(key, display);
      }
    }

    populateFromDiscoveryInstances(entity, template, byMetric, metricKeys);

    for (String key : metricKeys) {
      if (byMetric.containsKey(key)) {
        continue;
      }
      String display = resolveDisplayByTemplatePattern(template, key);
      if (display != null) {
        byMetric.put(key, display);
      }
    }

    return Map.copyOf(byMetric);
  }

  private void populateFromDiscoveryInstances(
      MonitoredDeviceEntity entity,
      ResolvedMonitoringTemplate template,
      Map<String, String> byMetric,
      Set<String> metricKeys
  ) {
    if (entity == null || template.discoveryRules() == null || template.discoveryRules().isEmpty()) {
      return;
    }

    Map<String, List<DiscoveryInstanceRuntime>> active = runtimeStateService.loadActiveDiscoveryInstances(entity);
    if (active == null || active.isEmpty()) {
      return;
    }

    for (var discoveryRule : template.discoveryRules().values()) {
      List<DiscoveryInstanceRuntime> instances = active.getOrDefault(discoveryRule.key(), List.of());
      if (instances.isEmpty()) {
        continue;
      }
      for (var prototype : discoveryRule.itemPrototypes()) {
        String keyTemplate = prototype.key();
        String nameTemplate = prototype.name();
        if (keyTemplate == null || keyTemplate.isBlank() || nameTemplate == null || nameTemplate.isBlank()) {
          continue;
        }
        for (DiscoveryInstanceRuntime instance : instances) {
          Map<String, String> macros = instance.macros() == null ? Map.of() : instance.macros();
          String materializedKey = applyMacros(keyTemplate, macros);
          if (!metricKeys.contains(materializedKey) || byMetric.containsKey(materializedKey)) {
            continue;
          }
          String display = normalizeDisplay(applyMacros(nameTemplate, macros));
          if (display != null) {
            byMetric.put(materializedKey, display);
          }
        }
      }
    }
  }

  private String resolveDisplayByTemplatePattern(ResolvedMonitoringTemplate template, String metricKey) {
    if (template == null || metricKey == null || metricKey.isBlank() || template.metrics() == null) {
      return null;
    }
    for (var entry : template.metrics().entrySet()) {
      String templateKey = entry.getKey();
      MetricDefinition definition = entry.getValue();
      String displayTemplate = definition == null ? null : definition.itemDisplayName();
      if (templateKey == null || displayTemplate == null || displayTemplate.isBlank()) {
        continue;
      }
      if (!templateKey.contains("{#")) {
        continue;
      }
      String resolved = resolveByKeyTemplate(metricKey, templateKey, displayTemplate);
      if (resolved != null) {
        return resolved;
      }
    }
    return null;
  }

  private String resolveByKeyTemplate(String metricKey, String templateKey, String displayTemplate) {
    Matcher macroMatcher = Pattern.compile("\\{#[A-Z0-9_.]+\\}").matcher(templateKey);
    StringBuilder regexBuilder = new StringBuilder("^");
    List<String> macroNames = new ArrayList<>();
    int last = 0;
    while (macroMatcher.find()) {
      String literal = templateKey.substring(last, macroMatcher.start());
      regexBuilder.append(Pattern.quote(literal));
      regexBuilder.append("(.+?)");
      macroNames.add(macroMatcher.group());
      last = macroMatcher.end();
    }
    regexBuilder.append(Pattern.quote(templateKey.substring(last)));
    regexBuilder.append("$");

    Matcher concrete = Pattern.compile(regexBuilder.toString()).matcher(metricKey);
    if (!concrete.matches()) {
      return null;
    }

    Map<String, String> macroValues = new LinkedHashMap<>();
    for (int i = 0; i < macroNames.size(); i++) {
      macroValues.put(macroNames.get(i), concrete.group(i + 1));
    }
    return normalizeDisplay(applyMacros(displayTemplate, macroValues));
  }

  private String applyMacros(String value, Map<String, String> macros) {
    if (value == null || value.isBlank() || macros == null || macros.isEmpty()) {
      return value;
    }
    String resolved = value;
    for (var macro : macros.entrySet()) {
      if (macro.getKey() == null || macro.getKey().isBlank() || macro.getValue() == null) {
        continue;
      }
      resolved = resolved.replace(macro.getKey(), macro.getValue());
    }
    return resolved;
  }

  private String normalizeDisplay(String value) {
    if (value == null || value.isBlank()) {
      return null;
    }
    String normalized = value.trim().replaceAll("\\s+", " ");
    return normalized.contains("{#") ? null : normalized;
  }

  private Optional<MonitoredDeviceEntity> resolveExistingEntity(DeviceScanResult scan) {
    Integer scanPort = normalizeSnmpPort(scan.port());
    Optional<MonitoredDeviceEntity> byIp = findByIpAndPort(scan.ip(), scanPort);
    if (byIp.isPresent()) {
      return byIp;
    }
    if (scanPort != null) {
      return Optional.empty();
    }
    if (meaningfulValue(scan.serialNumber())) {
      Optional<MonitoredDeviceEntity> bySerial = monitoredDeviceRepository
          .findFirstBySerialNumberIgnoreCase(scan.serialNumber());
      if (bySerial.isPresent()) {
        return bySerial;
      }
    }
    if (meaningfulValue(scan.macAddress())) {
      String normalizedMac = normalizeMac(scan.macAddress());
      return monitoredDeviceRepository.findAll().stream()
          .filter(e -> normalizedMac.equals(normalizeMac(e.getMacAddress())))
          .findFirst();
    }
    return Optional.empty();
  }

  private Optional<MonitoredDeviceEntity> findByIpAndPort(String ip, Integer snmpPort) {
    if (snmpPort == null) {
      return monitoredDeviceRepository.findFirstByIpAndSnmpPortIsNull(ip)
          .or(() -> monitoredDeviceRepository.findFirstByIpOrderByUpdatedAtDesc(ip));
    }
    return monitoredDeviceRepository.findFirstByIpAndSnmpPort(ip, snmpPort);
  }

  private MonitoredDeviceEntity resolveEntityForActivation(
      DeviceScanResult device,
      List<String> selectedTemplateIds,
      List<Map.Entry<String, String>> pendingIpMigrations,
      Map<MonitoredDeviceEntity, List<String>> beforeTemplatesByEntity,
      MonitoringSnmpCredentials snmpCredentials
  ) {
    Optional<MonitoredDeviceEntity> existing = resolveExistingEntity(device);

    MonitoredDeviceEntity entity;
    if (existing.isPresent()) {
      entity = existing.get();
      String oldIp = entity.getIp();
      if (!oldIp.equals(device.ip())) {
        pendingIpMigrations.add(Map.entry(oldIp, device.ip()));
      }
    } else {
      entity = new MonitoredDeviceEntity();
    }

    List<String> existingTemplateIds = MonitoringTemplateSelectionSupport.parseStored(
        entity.getTemplateIds(),
        entity.getTemplateId()
    );
    beforeTemplatesByEntity.put(entity, existingTemplateIds);
    List<String> templateSelection = selectedTemplateIds.isEmpty() ? existingTemplateIds : selectedTemplateIds;
    String primaryTemplateId = templateSelection.isEmpty() ? null : templateSelection.get(0);

    ResolvedMonitoringTemplate effectiveTemplate = templateResolver.resolveForDevice(
        templateSelection, device.vendor(), device.model(), device.firmwareVersion()
    );
    populateEntity(
        entity, device,
        entity.getCreatedAt() == null ? OffsetDateTime.now() : entity.getCreatedAt(),
        primaryTemplateId,
        templateSelection,
        effectiveTemplate
    );
    if (snmpCredentials != null) {
      MonitoringSnmpTemplateSupport.applyActivationSnmp(entity, device, snmpCredentials);
    } else if (entity.getSnmpVersion() == null || entity.getSnmpVersion().isBlank()) {
      entity.setSnmpVersion(MonitoringSnmpTemplateSupport.resolveVersionFromPollingStatus(device.pollingStatus()));
    }
    return entity;
  }

  private void publishMonitoringStateDiff(
      MonitoredDeviceEntity entity,
      List<String> beforeTemplateIds,
      List<String> afterTemplateIds
  ) {
    if (applicationEventPublisher == null || entity.getId() == null) {
      return;
    }
    String sourceSystem = currentSourceSystem();
    // Per-device snapshot (wiSLA NS integration contract, topic wisla.monitor-state).
    // Fire MONITOR_ON only when the device has effective templates after the change;
    // a transition to empty set means the device is no longer monitored and is handled below.
    if (afterTemplateIds != null && !afterTemplateIds.isEmpty()) {
      applicationEventPublisher.publishEvent(new WislaMonitorStateSnapshotEvent(
          monitorStateSnapshotMapper.forMonitorOn(entity, sourceSystem, afterTemplateIds)
      ));
    } else if (beforeTemplateIds != null && !beforeTemplateIds.isEmpty()) {
      applicationEventPublisher.publishEvent(new WislaMonitorStateSnapshotEvent(
          monitorStateSnapshotMapper.forMonitorOff(entity.getId(), sourceSystem)
      ));
    }
  }

  private void emitDeletedForEntities(List<MonitoredDeviceEntity> entities) {
    if (applicationEventPublisher == null || entities == null || entities.isEmpty()) {
      return;
    }
    String sourceSystem = currentSourceSystem();
    for (MonitoredDeviceEntity entity : entities) {
      applicationEventPublisher.publishEvent(new WislaMonitorStateSnapshotEvent(
          monitorStateSnapshotMapper.forDeleted(entity.getId(), sourceSystem)
      ));
    }
  }

  private String currentSourceSystem() {
    return sourceSystemProvider != null ? sourceSystemProvider.getSourceSystem() : "networkscanner";
  }

  private String resolveUnitLabel(
      String metricName, Map<String, MetricDefinition> metrics, Map<String, UnitDefinition> units
  ) {
    if (metrics == null || metrics.isEmpty()) {
      return "";
    }
    MetricDefinition definition = metrics.get(metricName);
    if (definition == null || definition.unit() == null || definition.unit().isBlank()) {
      return "";
    }
    if (units != null && !units.isEmpty()) {
      UnitDefinition unitDef = units.get(definition.unit());
      if (unitDef != null && unitDef.label() != null && !unitDef.label().isBlank()) {
        return unitDef.label();
      }
    }
    String canonical = UnitScaleCatalog.canonicalUnit(definition.unit());
    return canonical != null && !canonical.isBlank() ? canonical : "";
  }

  private List<DeviceScanResult> listStoredDevices() {
    return monitoredDeviceRepository.findAll().stream()
        .map(this::toResult)
        .toList();
  }

  private long countHostsByAvailability(
      Specification<MonitoredDeviceEntity> baseSpec,
      MonitoringHostAvailabilityFilter availability
  ) {
    Specification<MonitoredDeviceEntity> availabilitySpec =
        MonitoredDeviceSpecifications.hostAvailability(availability);
    if (baseSpec == null) {
      return monitoredDeviceRepository.count(availabilitySpec);
    }
    return monitoredDeviceRepository.count(baseSpec.and(availabilitySpec));
  }

  private String firstNonBlank(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private String formatCreatedAt(OffsetDateTime createdAt) {
    if (createdAt == null) {
      return "-";
    }
    return createdAt.atZoneSameInstant(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
  }

  private MonitoredDeviceEntity populateEntity(
      MonitoredDeviceEntity entity, DeviceScanResult result,
      OffsetDateTime createdAt,
      String templateId,
      List<String> templateIds,
      ResolvedMonitoringTemplate effectiveTemplate
  ) {
    entity.setIp(result.ip());
    entity.setHostName(result.hostName());
    entity.setDomainName(result.domainName() == null || result.domainName().isBlank() ? "-" : result.domainName());
    entity.setName(result.name());
    entity.setSerialNumber(result.serialNumber());
    entity.setMacAddress(result.macAddress());
    entity.setVendor(result.vendor());
    entity.setModel(result.model());
    entity.setFirmwareVersion(result.firmwareVersion());
    entity.setPollingStatus(result.pollingStatus());
    entity.setStatus(result.status());
    if (entity.getHealthStatus() == null) {
      entity.setHealthStatus(DeviceHealthStatus.NORM);
    }
    entity.setGroupName(result.group());
    List<String> mergedTags = SnmpDeviceTypeClassifier.mergeDeviceTypeTag(
        readTags(entity.getTagsJson()),
        result.tags()
    );
    entity.setTagsJson(writeTags(normalizeTags(mergedTags)));
    entity.setAvailabilityJson(writeAvailability(result.availability()));
    entity.setTemplateId(templateId);
    entity.setTemplateIds(MonitoringTemplateSelectionSupport.toStored(templateIds));
    entity.setEffectiveTemplateId(effectiveTemplate.id());
    entity.setTemplateVersion(effectiveTemplate.templateVersion());
    entity.setPackVersion(effectiveTemplate.packVersion());
    entity.setSchemaVersion(effectiveTemplate.schemaVersion());
    entity.setSnmpPort(resolveStoredSnmpPort(result.port(), entity.getSnmpPort()));
    entity.setCreatedAt(createdAt == null ? OffsetDateTime.now() : createdAt);
    entity.setUpdatedAt(OffsetDateTime.now());
    return entity;
  }

  private List<String> resolveTemplateSelection(
      String ip,
      String batchTemplateId,
      List<String> batchTemplateIds,
      Map<String, String> perDeviceTemplateIds,
      Map<String, List<String>> perDeviceTemplateIdLists
  ) {
    if (perDeviceTemplateIdLists != null && perDeviceTemplateIdLists.containsKey(ip)) {
      return MonitoringTemplateSelectionSupport.normalize(perDeviceTemplateIdLists.get(ip));
    }
    if (perDeviceTemplateIds != null && perDeviceTemplateIds.containsKey(ip)) {
      return MonitoringTemplateSelectionSupport.normalize(List.of(perDeviceTemplateIds.get(ip)));
    }
    List<String> normalizedBatch = MonitoringTemplateSelectionSupport.normalize(batchTemplateIds);
    if (!normalizedBatch.isEmpty()) {
      return normalizedBatch;
    }
    if (batchTemplateId != null && !batchTemplateId.isBlank()) {
      return List.of(batchTemplateId.trim());
    }
    return List.of();
  }

  private ResolvedMonitoringTemplate resolveTemplateFor(MonitoredDeviceEntity entity, DeviceScanResult device) {
    ResolvedMonitoringTemplate template = templateResolver.resolveForDevice(
        MonitoringTemplateSelectionSupport.parseStored(entity.getTemplateIds(), entity.getTemplateId()),
        device.vendor(),
        device.model(),
        device.firmwareVersion()
    );
    return MonitoringSnmpTemplateSupport.applyDeviceSnmpOverrides(template, entity);
  }

  private DeviceScanResult toResult(MonitoredDeviceEntity entity) {
    return new DeviceScanResult(
        entity.getHostName(), entity.getName(), entity.getSerialNumber(),
        entity.getIp(),
        entity.getDomainName() == null || entity.getDomainName().isBlank() ? "-" : entity.getDomainName(),
        entity.getMacAddress(), entity.getVendor(),
        entity.getModel(), entity.getFirmwareVersion(), entity.getPollingStatus(),
        entity.getStatus(), entity.getGroupName(),
        readTags(entity.getTagsJson()),
        readAvailability(entity.getAvailabilityJson()),
        entity.getSnmpPort(),
        entity.getId()
    );
  }

  private Integer resolveStoredSnmpPort(Integer scannedPort, Integer existingPort) {
    Integer normalizedScannedPort = normalizeSnmpPort(scannedPort);
    if (normalizedScannedPort != null) {
      return normalizedScannedPort;
    }
    return existingPort;
  }

  private Integer normalizeSnmpPort(Integer port) {
    if (port == null || port <= 0) {
      return null;
    }
    return port;
  }

  private DeviceInterfaceDto toInterfaceDto(MonitoredDeviceInterfaceEntity entity) {
    return new DeviceInterfaceDto(
        entity.getName(),
        entity.getDescription(),
        entity.getAdminStatus(),
        entity.getOperStatus(),
        entity.getLost(),
        entity.getNominalSpeed(),
        entity.getActiveSpeed(),
        entity.getPurpose(),
        entity.getMode(),
        entity.getKind()
    );
  }

  private MonitoredDeviceInterfaceEntity toInterfaceEntity(
      MonitoredDeviceEntity device,
      MonitoredDeviceInterfaceEntity target,
      DeviceInterfaceDto source,
      String lost,
      OffsetDateTime updatedAt
  ) {
    MonitoredDeviceInterfaceEntity entity = target == null ? new MonitoredDeviceInterfaceEntity() : target;
    entity.setDevice(device);
    entity.setName(source.name());
    entity.setDescription(source.description());
    entity.setAdminStatus(source.adminStatus());
    entity.setOperStatus(source.operStatus());
    entity.setLost(lost);
    entity.setNominalSpeed(source.nominalSpeed());
    entity.setActiveSpeed(source.activeSpeed());
    entity.setPurpose(source.purpose());
    entity.setMode(source.mode());
    entity.setKind(source.kind());
    entity.setUpdatedAt(updatedAt);
    return entity;
  }

  private MonitoringHostRowDto toMonitoringHostRow(MonitoredDeviceEntity entity) {
    return new MonitoringHostRowDto(
        entity.getId(),
        entity.getHostName(),
        entity.getName(),
        entity.getSerialNumber(),
        entity.getIp(),
        entity.getDomainName() == null || entity.getDomainName().isBlank() ? "-" : entity.getDomainName(),
        entity.getSnmpPort(),
        entity.getMacAddress(),
        entity.getVendor(),
        entity.getModel(),
        entity.getFirmwareVersion(),
        entity.getPollingStatus(),
        entity.getStatus(),
        entity.getHealthStatus() == null ? null : entity.getHealthStatus().name(),
        entity.getGroupName(),
        readTags(entity.getTagsJson()),
        readAvailability(entity.getAvailabilityJson())
    );
  }

  private MonitoredDeviceDto toMonitoredDeviceDto(MonitoredDeviceEntity entity) {
    return new MonitoredDeviceDto(
        entity.getId(),
        entity.getIp(),
        entity.getSnmpPort(),
        entity.getHostName(),
        entity.getName(),
        entity.getSerialNumber(),
        entity.getMacAddress(),
        entity.getVendor(),
        entity.getModel(),
        entity.getFirmwareVersion(),
        entity.getPollingStatus(),
        entity.getStatus(),
        entity.getHealthStatus() == null ? null : entity.getHealthStatus().name(),
        entity.getGroupName(),
        readTags(entity.getTagsJson()),
        readAvailability(entity.getAvailabilityJson()),
        entity.getTemplateId(),
        MonitoringTemplateSelectionSupport.parseStored(entity.getTemplateIds(), entity.getTemplateId()),
        entity.getEffectiveTemplateId(),
        entity.getTemplateVersion(),
        entity.getPackVersion(),
        entity.getSchemaVersion(),
        entity.getCreatedAt(),
        entity.getUpdatedAt()
    );
  }

  private static boolean meaningfulValue(String value) {
    return value != null && !value.isBlank() && !"-".equals(value);
  }

  private static String normalizeMac(String mac) {
    if (mac == null) {
      return "";
    }
    return mac.replaceAll("[:\\-.]", "").toUpperCase();
  }

  private String writeAvailability(List<AvailabilityDto> availability) {
    try {
      return objectMapper.writeValueAsString(availability);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Не удалось сериализовать доступность устройства.", exception);
    }
  }

  private List<AvailabilityDto> readAvailability(String availabilityJson) {
    try {
      return objectMapper.readValue(availabilityJson, new TypeReference<List<AvailabilityDto>>() {
      });
    } catch (JsonProcessingException exception) {
      return List.of();
    }
  }

  private String writeTags(List<String> tags) {
    try {
      return objectMapper.writeValueAsString(tags == null ? List.of() : tags);
    } catch (JsonProcessingException exception) {
      throw new IllegalStateException("Не удалось сериализовать теги устройства.", exception);
    }
  }

  private List<String> readTags(String tagsJson) {
    try {
      if (tagsJson == null || tagsJson.isBlank()) {
        return List.of();
      }
      return objectMapper.readValue(tagsJson, new TypeReference<List<String>>() {
      });
    } catch (JsonProcessingException exception) {
      return List.of();
    }
  }

  private List<String> normalizeTags(List<String> tags) {
    if (tags == null || tags.isEmpty()) {
      return List.of();
    }
    LinkedHashSet<String> out = new LinkedHashSet<>();
    for (String raw : tags) {
      if (raw == null) continue;
      String t = raw.trim().replaceAll("\\s+", " ");
      if (t.isEmpty()) continue;
      if (t.length() > 64) {
        t = t.substring(0, 64);
      }
      out.add(t);
      if (out.size() >= 20) break;
    }
    return List.copyOf(out);
  }

  /**
   * Паттерн для {@code ILIKE ... ESCAPE '\\'}; {@code null} — не применять фильтр по подстроке.
   */
  private static String toIlikeContainsPattern(String raw) {
    if (raw == null) {
      return null;
    }
    String t = raw.trim();
    if (t.isEmpty()) {
      return null;
    }
    String escaped = t.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    return "%" + escaped + "%";
  }

  private void validateMonitoringEventFilterDevices(MonitoringEventFilter filter) {
    if (filter.deviceId() != null) {
      findEntityById(filter.deviceId());
    }
    if (filter.deviceIds() != null) {
      for (Long id : filter.deviceIds()) {
        if (id != null) {
          findEntityById(id);
        }
      }
    }
  }

  private static String toDeviceIdsCsv(List<Long> deviceIds) {
    if (deviceIds == null || deviceIds.isEmpty()) {
      return null;
    }
    StringBuilder out = new StringBuilder();
    for (Long id : deviceIds) {
      if (id == null || id <= 0) {
        continue;
      }
      if (!out.isEmpty()) {
        out.append(',');
      }
      out.append(id);
    }
    return out.isEmpty() ? null : out.toString();
  }

  private static String toDeviceTagsCsv(String deviceTags) {
    if (deviceTags == null) {
      return null;
    }
    String[] parts = deviceTags.split(",");
    StringBuilder out = new StringBuilder();
    for (String part : parts) {
      if (part == null) {
        continue;
      }
      String tag = part.trim();
      if (tag.isEmpty()) {
        continue;
      }
      if (!out.isEmpty()) {
        out.append(',');
      }
      out.append(tag);
    }
    return out.isEmpty() ? null : out.toString();
  }

  private MonitoringEventDto toEventDto(MonitoringEventEntity event, String metricDisplayName) {
    MonitoredDeviceEntity device = event.getDevice();
    return new MonitoringEventDto(
        event.getId(),
        device.getId(),
        device.getIp(),
        device.getName(),
        device.getHostName(),
        device.getMacAddress(),
        event.getTemplateId(),
        event.getMetricName(),
        metricDisplayName,
        event.getTriggerName(),
        event.getTriggerExpression(),
        event.getRecoveryExpression(),
        event.getRecoveryPath(),
        event.getThresholdLevel().name(),
        event.getThresholdValue(),
        event.getActualValue(),
        event.getBreachStartedAt(),
        event.getNormalizedAt(),
        event.getStatus().name()
    );
  }

  private MonitoringItemStateDto toItemStateDto(
      com.networkscanner.backend.monitoring.dto.ItemStateSnapshot item,
      String itemDisplayName,
      List<MetricChartThresholdDto> thresholds,
      ResolvedMonitoringTemplate template
  ) {
    String rawValue = item.numericValue() != null ? formatNumericValue(item.numericValue()) : item.textValue();
    String mappedValue = templateResolver.mapValue(item.templateId(), item.valueMapName(), rawValue);
    UnitScalingService.ScalingResult scaled = unitScalingService.scaleSingle(item.numericValue(), item.unitLabel());
    String presentationValue = mappedValue;
    if (item.unitLabel() != null && !item.unitLabel().isBlank()
        && mappedValue != null && mappedValue.equals(rawValue)) {
      presentationValue = scaled.displayValue();
    }
    String scaledDisplayValue = mappedValue != null && mappedValue.equals(rawValue)
        ? scaled.displayValue()
        : mappedValue;
    List<MetricChartThresholdDto> itemThresholds = thresholds == null ? List.of() : thresholds;
    if (!itemThresholds.isEmpty() && item.unitLabel() != null && !item.unitLabel().isBlank()) {
      double maxAbs = item.numericValue() == null || !Double.isFinite(item.numericValue())
          ? 0d
          : Math.abs(item.numericValue());
      UnitScalingService.SeriesScale scale = unitScalingService.resolveSeriesScale(item.unitLabel(), maxAbs);
      itemThresholds = itemThresholds.stream()
          .map(threshold -> new MetricChartThresholdDto(
              threshold.metricName(),
              threshold.instanceKey(),
              threshold.triggerName(),
              threshold.triggerUuid(),
              threshold.thresholdLevel(),
              threshold.thresholdValue(),
              unitScalingService.applySeriesScale(threshold.thresholdValue(), item.unitLabel(), scale).scaledValue(),
              threshold.operator(),
              threshold.dynamic(),
              threshold.seriesT(),
              threshold.seriesV(),
              threshold.seriesSv(),
              threshold.valueMapMappings()
          ))
          .toList();
    }
    itemThresholds = enrichThresholdValueMaps(itemThresholds, template);
    ValueMapSeriesMeta itemValueMap = ValueMapSeriesResolver.resolve(template, item.itemKey());
    Map<String, String> itemValueMapMappings =
        itemValueMap == null ? null : itemValueMap.mappings();
    return new MonitoringItemStateDto(
        item.itemKey(),
        blankToNull(itemDisplayName),
        blankToNull(item.instanceKey()),
        item.numericValue(),
        item.textValue(),
        item.unitLabel(),
        scaled.scaledValue(),
        scaled.scaledUnit(),
        scaledDisplayValue,
        item.valueMapName(),
        itemValueMapMappings,
        presentationValue,
        blankToNull(item.preprocessingStatus()),
        blankToNull(item.preprocessingNote()),
        item.lastCollectedAt(),
        itemThresholds
    );
  }

  private List<MetricChartThresholdDto> enrichThresholdValueMaps(
      List<MetricChartThresholdDto> thresholds,
      ResolvedMonitoringTemplate template
  ) {
    if (thresholds == null || thresholds.isEmpty() || template == null) {
      return thresholds == null ? List.of() : thresholds;
    }
    List<MetricChartThresholdDto> enriched = new ArrayList<>(thresholds.size());
    for (MetricChartThresholdDto threshold : thresholds) {
      Map<String, String> mappings = threshold.valueMapMappings();
      if (mappings == null || mappings.isEmpty()) {
        ValueMapSeriesMeta meta = ValueMapSeriesResolver.resolve(template, threshold.metricName());
        mappings = meta == null ? null : meta.mappings();
      }
      if (mappings == null || mappings.isEmpty() || mappings == threshold.valueMapMappings()) {
        enriched.add(threshold);
        continue;
      }
      enriched.add(new MetricChartThresholdDto(
          threshold.metricName(),
          threshold.instanceKey(),
          threshold.triggerName(),
          threshold.triggerUuid(),
          threshold.thresholdLevel(),
          threshold.thresholdValue(),
          threshold.scaledThresholdValue(),
          threshold.operator(),
          threshold.dynamic(),
          threshold.seriesT(),
          threshold.seriesV(),
          threshold.seriesSv(),
          Map.copyOf(mappings)
      ));
    }
    return List.copyOf(enriched);
  }

  private String formatNumericValue(Double value) {
    if (value == null) {
      return null;
    }
    if (Math.abs(value - Math.rint(value)) < 0.000001d) {
      return String.valueOf(value.longValue());
    }
    return String.valueOf(value);
  }

  private String blankToNull(String value) {
    return value == null || value.isBlank() ? null : value;
  }

  private static String summarizeIps(List<String> ips) {
    if (ips == null || ips.isEmpty()) {
      return "";
    }
    String joined = String.join(", ", ips);
    return joined.length() > 400 ? joined.substring(0, 399) + "…" : joined;
  }

  private static String summarizeIds(List<Long> ids) {
    if (ids == null || ids.isEmpty()) {
      return "";
    }
    String joined = ids.stream().map(String::valueOf).collect(java.util.stream.Collectors.joining(", "));
    return joined.length() > 400 ? joined.substring(0, 399) + "…" : joined;
  }
}
