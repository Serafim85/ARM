package com.networkscanner.backend.monitoring.web;

import com.networkscanner.backend.accessprofiles.api.AccessProfileResolver;
import com.networkscanner.backend.inventory.api.ConfigBackupService;
import com.networkscanner.backend.monitoring.dto.MonitoringSnmpCredentials;
import com.networkscanner.backend.inventory.dto.BackupBaselineUploadRequest;
import com.networkscanner.backend.inventory.dto.BackupComparisonResult;
import com.networkscanner.backend.inventory.dto.BackupSelectionRequest;
import com.networkscanner.backend.inventory.dto.DeviceBackupSnapshot;
import com.networkscanner.backend.monitoring.api.MonitoringService;
import com.networkscanner.backend.monitoring.dto.CompactMetricsBatchSeriesDto;
import com.networkscanner.backend.monitoring.dto.CompactMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.DeviceInterfaceDto;
import com.networkscanner.backend.monitoring.dto.DeviceTagsUpdateRequest;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.MonitoredDeviceDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDiscoveryInstanceDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeactivateRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemDto;
import com.networkscanner.backend.monitoring.dto.MonitoringDeviceItemsUpdateRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringDevicesRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringEventDto;
import com.networkscanner.backend.monitoring.dto.MonitoringEventFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringEventLevelSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringEventPageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringHostAvailabilityFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringHostFilter;
import com.networkscanner.backend.monitoring.dto.MonitoringHostPageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringItemStateDto;
import com.networkscanner.backend.monitoring.dto.MonitoringItemStatePageDto;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateDetailsDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateImportPreviewDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOperationResultDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSummaryDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateUpdateRequest;
import com.networkscanner.backend.monitoring.model.DeviceHealthStatus;
import com.networkscanner.backend.monitoring.model.MonitoringEventStatus;
import com.networkscanner.backend.monitoring.model.ThresholdLevel;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/monitoring")
@Tag(
    name = "Мониторинг",
    description = "Список устройств на мониторинге, активация/деактивация, метрики, интерфейсы, детали SNMP и бэкапы конфигураций. "
        + "GET — роли ADMIN, OPERATOR, VIEWER; изменение бэкапов/эталона — только ADMIN; активация/деактивация — ADMIN, OPERATOR."
)
public class MonitoringController {

  private static final Logger log = LoggerFactory.getLogger(MonitoringController.class);

  /** Максимум точек на ряд для графиков SPA по умолчанию (если клиент не задал {@code maxPoints}). */
  private static final int DEFAULT_CHART_MAX_POINTS = 1500;

  private final MonitoringService monitoringService;
  private final ConfigBackupService configBackupService;
  private final AccessProfileResolver accessProfileResolver;

  public MonitoringController(
      MonitoringService monitoringService,
      ConfigBackupService configBackupService,
      AccessProfileResolver accessProfileResolver
  ) {
    this.monitoringService = monitoringService;
    this.configBackupService = configBackupService;
    this.accessProfileResolver = accessProfileResolver;
  }

  @GetMapping
  @Operation(
      summary = "Список устройств на мониторинге",
      description = "Возвращает устройства на мониторинге с серверными фильтрами, поиском, сортировкой и пагинацией."
  )
  public MonitoringHostPageDto list(
      @Parameter(description = "Общий поиск по имени/хосту/IP/MAC")
      @RequestParam(required = false) String q,
      @Parameter(description = "Фильтр по IP-адресу")
      @RequestParam(required = false) String ip,
      @Parameter(description = "Фильтр по MAC-адресу")
      @RequestParam(required = false) String macAddress,
      @Parameter(description = "Фильтр по статусу опроса")
      @RequestParam(required = false) String status,
      @Parameter(description = "Фильтр по тегу устройства (точное совпадение по одному тегу)")
      @RequestParam(required = false) String tag,
      @Parameter(description = "Фильтр по health status: NORM / WARN / CRITICAL")
      @RequestParam(required = false) DeviceHealthStatus healthStatus,
      @Parameter(description = "Фильтр по доступности хоста: AVAILABLE / UNAVAILABLE / UNKNOWN")
      @RequestParam(required = false) MonitoringHostAvailabilityFilter availability,
      @Parameter(description = "Номер страницы (с нуля)")
      @RequestParam(required = false, defaultValue = "0") int page,
      @Parameter(description = "Размер страницы (1-200; 0 или отсутствие — 15)")
      @RequestParam(required = false, defaultValue = "0") int size,
      @Parameter(description = "Поле сортировки: name, hostName, ip, macAddress, model, status, healthStatus, availability, group")
      @RequestParam(required = false, defaultValue = "ip") String sortField,
      @Parameter(description = "Направление сортировки: asc / desc")
      @RequestParam(required = false, defaultValue = "asc") String sortOrder
  ) {
    MonitoringHostFilter filter = new MonitoringHostFilter(
        q,
        ip,
        macAddress,
        status,
        tag,
        healthStatus,
        availability
    );
    return monitoringService.list(filter, page, size, sortField, sortOrder);
  }

  @GetMapping("/templates")
  @Operation(
      summary = "Шаблоны мониторинга SNMP",
      description = "Краткий перечень доступных шаблонов для привязки к устройствам при активации."
  )
  public List<MonitoringTemplateSummaryDto> templates() {
    return monitoringService.listMonitoringTemplates();
  }

  @GetMapping("/templates/{templateId}")
  @Operation(
      summary = "Подробности шаблона мониторинга",
      description = "Возвращает расширенное описание шаблона: coverage report, items, discovery rules, triggers, valuemaps и graphs metadata."
  )
  public MonitoringTemplateDetailsDto templateDetails(@PathVariable String templateId) {
    return monitoringService.getMonitoringTemplateDetails(templateId);
  }

  @PostMapping(path = "/templates/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Предпросмотр файла шаблона мониторинга",
      description = "Dry-run разбора и компиляции файла шаблона (.template) без сохранения в БД. "
          + "Возвращает coverage report и diff относительно существующего шаблона."
  )
  public MonitoringTemplateImportPreviewDto previewTemplate(
      @RequestParam("file") MultipartFile file
  ) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо выбрать файл шаблона.");
    }
    try {
      return monitoringService.previewMonitoringTemplateArchive(file.getOriginalFilename(), file.getBytes());
    } catch (java.io.IOException exception) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать загруженный файл.", exception);
    }
  }

  @PostMapping(path = "/templates/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @Operation(
      summary = "Загрузить файл шаблона мониторинга",
      description = "Принимает обфусцированный файл шаблона (.template) с Zabbix export внутри. Доступно только ADMIN."
  )
  public MonitoringTemplateOperationResultDto uploadTemplate(
      @RequestParam("file") MultipartFile file,
      @RequestParam("vendor") String vendor,
      @RequestParam(value = "model", required = false) String model,
      @RequestParam(value = "firmware", required = false) String firmware,
      Authentication authentication
  ) {
    if (file == null || file.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо выбрать файл шаблона.");
    }
    if (vendor == null || vendor.isBlank()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Необходимо заполнить поле «Вендор».");
    }
    try {
      return monitoringService.uploadMonitoringTemplateArchive(
          file.getOriginalFilename(),
          file.getBytes(),
          vendor,
          model,
          firmware,
          authentication
      );
    } catch (java.io.IOException exception) {
      log.warn(
          "Не удалось прочитать тело multipart при загрузке шаблона (fileName={}): {}",
          file.getOriginalFilename(),
          exception.getMessage(),
          exception
      );
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не удалось прочитать загруженный файл.", exception);
    }
  }

  @DeleteMapping("/templates/{templateId}")
  @Operation(
      summary = "Удалить загруженный шаблон мониторинга",
      description = "Удаляет только пользовательский шаблон, если он не используется и не является системным."
  )
  public MonitoringTemplateOperationResultDto deleteTemplate(
      @PathVariable String templateId,
      Authentication authentication
  ) {
    return monitoringService.deleteMonitoringTemplate(templateId, authentication);
  }

  @PatchMapping("/templates/{templateId}")
  @Operation(
      summary = "Обновить метаданные шаблона мониторинга",
      description = "Для загруженных шаблонов — вендор, модель, прошивка и приоритет. "
          + "Для системных — только приоритет. Доступно только ADMIN."
  )
  public MonitoringTemplateOperationResultDto updateTemplate(
      @PathVariable String templateId,
      @RequestBody MonitoringTemplateUpdateRequest request,
      Authentication authentication
  ) {
    return monitoringService.updateMonitoringTemplate(templateId, request, authentication);
  }

  @PostMapping("/activate")
  @Operation(
      summary = "Добавить устройства на мониторинг",
      description = "Сохраняет устройства из тела запроса, назначает один или несколько шаблонов (общие и/или per-device), создаёт записи бэкапов при необходимости."
  )
  public List<DeviceScanResult> activate(
      @Valid @RequestBody MonitoringDevicesRequest request,
      Authentication authentication
  ) {
    MonitoringSnmpCredentials snmpCredentials = request.snmpCredentials();
    if (snmpCredentials == null && request.accessProfileIdForActivation() != null) {
      snmpCredentials = accessProfileResolver.resolveSnmpCredentials(request.accessProfileIdForActivation());
    }
    return monitoringService.activate(
        request.devices(),
        request.templateId(),
        request.templateIds(),
        request.perDeviceTemplateIds(),
        request.perDeviceTemplateIdLists(),
        snmpCredentials,
        authentication
    );
  }

  @PostMapping("/deactivate")
  @Operation(
      summary = "Снять устройства с мониторинга",
      description = "Удаляет устройства по списку deviceIds или ips (одно из полей обязательно). Удаляет связанные данные бэкапов."
  )
  public List<DeviceScanResult> deactivate(
      @RequestBody MonitoringDeactivateRequest request,
      Authentication authentication
  ) {
    if (request.hasIds()) {
      return monitoringService.deactivateByIds(request.deviceIds(), authentication);
    }
    if (request.hasIps()) {
      return monitoringService.deactivate(request.ips(), authentication);
    }
    throw new IllegalArgumentException("Необходимо указать deviceIds или ips.");
  }

  @PostMapping("/match-scan")
  @Operation(
      summary = "Сопоставить результат сканирования с мониторингом",
      description = "Для каждого элемента из тела подставляет monitoredDeviceId, если устройство уже есть в БД (по IP, серийному номеру или MAC)."
  )
  public List<DeviceScanResult> matchScan(@RequestBody List<DeviceScanResult> scanned) {
    return monitoringService.matchScanResults(scanned);
  }

  @GetMapping("/events/level-summary")
  @Operation(
      summary = "Сводка по уровням порога для событий мониторинга",
      description = "Количество событий по каждому threshold_level (DISASTER, HIGH, AVERAGE, WARNING, INFORMATION, NOT_CLASSIFIED) "
          + "для той же выборки, что и у GET /events с теми же фильтрами (без пагинации)."
  )
  public MonitoringEventLevelSummaryDto eventLevelSummary(
      @Parameter(description = "Статус OPEN или RESOLVED")
      @RequestParam(required = false) MonitoringEventStatus status,
      @Parameter(description = "ID записи monitored_devices")
      @RequestParam(required = false) Long deviceId,
      @Parameter(description = "Список ID monitored_devices (события только с этих устройств)")
      @RequestParam(required = false) List<Long> deviceIds,
      @Parameter(description = "Теги устройств CSV (OR; фильтр по tags_json)")
      @RequestParam(required = false) String deviceTags,
      @Parameter(description = "Начало интервала по breachStartedAt (включительно)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      OffsetDateTime breachStartedFrom,
      @Parameter(description = "Конец интервала по breachStartedAt (включительно)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      OffsetDateTime breachStartedTo,
      @Parameter(description = "Начало интервала по normalizedAt (только события с датой нормализации)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      OffsetDateTime normalizedFrom,
      @Parameter(description = "Конец интервала по normalizedAt (включительно)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      OffsetDateTime normalizedTo,
      @Parameter(description = "Минимальная длительность инцидента в секундах")
      @RequestParam(required = false) Long minDurationSeconds,
      @Parameter(description = "Максимальная длительность инцидента в секундах")
      @RequestParam(required = false) Long maxDurationSeconds,
      @Parameter(description = "Уровень порога (severity) thresholdLevel: DISASTER, HIGH, AVERAGE, WARNING, INFORMATION, NOT_CLASSIFIED")
      @RequestParam(required = false) ThresholdLevel thresholdLevel,
      @Parameter(description = "Подстрока в имени метрики (без учёта регистра)")
      @RequestParam(required = false) String metricNameContains,
      @Parameter(description = "Подстрока в MAC-адресе устройства (без учёта регистра)")
      @RequestParam(required = false) String macAddressContains,
      @Parameter(description = "Подстрока в IP устройства (без учёта регистра)")
      @RequestParam(required = false) String deviceIpContains,
      @Parameter(description = "Подстрока в имени устройства (поле name, без учёта регистра)")
      @RequestParam(required = false) String deviceNameContains
  ) {
    MonitoringEventFilter filter = new MonitoringEventFilter(
        status,
        deviceId,
        deviceIds,
        deviceTags,
        breachStartedFrom,
        breachStartedTo,
        normalizedFrom,
        normalizedTo,
        minDurationSeconds,
        maxDurationSeconds,
        thresholdLevel,
        metricNameContains,
        macAddressContains,
        deviceIpContains,
        deviceNameContains
    );
    return monitoringService.summarizeMonitoringEventsByLevel(filter);
  }

  @GetMapping("/events")
  @Operation(
      summary = "Список событий мониторинга",
      description = "Пороговые события с пагинацией (page, size). Все фильтры опциональны: статус, устройство "
          + "(deviceId), подстрока в имени устройства (поле name в monitored_devices), интервалы breachStartedAt / normalizedAt (ISO-8601), "
          + "длительность инцидента в секундах (min/max; для OPEN верхняя граница считается до текущего времени на сервере БД), "
          + "подстрока в имени метрики и отдельно в MAC-адресе устройства. "
          + "Размер страницы по умолчанию 20, не более 200."
  )
  public MonitoringEventPageDto listEvents(
      @Parameter(description = "Статус OPEN или RESOLVED")
      @RequestParam(required = false) MonitoringEventStatus status,
      @Parameter(description = "ID записи monitored_devices")
      @RequestParam(required = false) Long deviceId,
      @Parameter(description = "Список ID monitored_devices (события только с этих устройств)")
      @RequestParam(required = false) List<Long> deviceIds,
      @Parameter(description = "Теги устройств CSV (OR; фильтр по tags_json)")
      @RequestParam(required = false) String deviceTags,
      @Parameter(description = "Начало интервала по breachStartedAt (включительно)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      OffsetDateTime breachStartedFrom,
      @Parameter(description = "Конец интервала по breachStartedAt (включительно)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      OffsetDateTime breachStartedTo,
      @Parameter(description = "Начало интервала по normalizedAt (только события с датой нормализации)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      OffsetDateTime normalizedFrom,
      @Parameter(description = "Конец интервала по normalizedAt (включительно)")
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
      OffsetDateTime normalizedTo,
      @Parameter(description = "Минимальная длительность инцидента в секундах")
      @RequestParam(required = false) Long minDurationSeconds,
      @Parameter(description = "Максимальная длительность инцидента в секундах")
      @RequestParam(required = false) Long maxDurationSeconds,
      @Parameter(description = "Уровень порога (severity) thresholdLevel: DISASTER, HIGH, AVERAGE, WARNING, INFORMATION, NOT_CLASSIFIED")
      @RequestParam(required = false) ThresholdLevel thresholdLevel,
      @Parameter(description = "Подстрока в имени метрики (без учёта регистра)")
      @RequestParam(required = false) String metricNameContains,
      @Parameter(description = "Подстрока в MAC-адресе устройства (без учёта регистра)")
      @RequestParam(required = false) String macAddressContains,
      @Parameter(description = "Подстрока в IP устройства (без учёта регистра)")
      @RequestParam(required = false) String deviceIpContains,
      @Parameter(description = "Подстрока в имени устройства (поле name, без учёта регистра)")
      @RequestParam(required = false) String deviceNameContains,
      @Parameter(description = "Номер страницы (с нуля)")
      @RequestParam(required = false, defaultValue = "0") int page,
      @Parameter(description = "Размер страницы (1–200; 0 или отсутствие — 20)")
      @RequestParam(required = false, defaultValue = "0") int size
  ) {
    MonitoringEventFilter filter = new MonitoringEventFilter(
        status,
        deviceId,
        deviceIds,
        deviceTags,
        breachStartedFrom,
        breachStartedTo,
        normalizedFrom,
        normalizedTo,
        minDurationSeconds,
        maxDurationSeconds,
        thresholdLevel,
        metricNameContains,
        macAddressContains,
        deviceIpContains,
        deviceNameContains
    );
    return monitoringService.listMonitoringEvents(filter, page, size);
  }

  @GetMapping("/devices/{deviceId}/events")
  @Operation(
      summary = "События мониторинга по ID устройства",
      description = "Все пороговые события конкретного устройства, отсортированные по дате начала нарушения (убывание)."
  )
  public List<MonitoringEventDto> deviceEvents(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return monitoringService.getEventsByDeviceId(deviceId);
  }

  @GetMapping("/devices/{deviceId}")
  @Operation(
      summary = "Устройство по ID записи мониторинга",
      description = "Возвращает DeviceScanResult по первичному ключу monitored_devices."
  )
  public DeviceScanResult getDevice(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return monitoringService.getByDeviceId(deviceId);
  }

  @PatchMapping("/devices/{deviceId}/tags")
  @Operation(
      summary = "Обновить теги устройства",
      description = "Сохраняет список тегов (строки) для устройства. В запросе передаётся полный список тегов."
  )
  public MonitoredDeviceDto updateDeviceTags(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Valid @RequestBody DeviceTagsUpdateRequest request,
      Authentication authentication
  ) {
    return monitoringService.updateDeviceTags(deviceId, request.tags(), authentication);
  }

  @GetMapping("/devices/{deviceId}/meta")
  @Operation(
      summary = "Метаданные устройства на мониторинге",
      description = "Возвращает служебные поля monitored_devices: templateId, effectiveTemplateId, версии шаблона и timestamps."
  )
  public MonitoredDeviceDto getDeviceMeta(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return monitoringService.getMonitoredDeviceById(deviceId);
  }

  @GetMapping("/devices/{deviceId}/metrics")
  @Operation(
      summary = "История метрик по ID устройства",
      description = "Выборка метрик из хранилища с подписью единиц измерения. Фильтры: интервал времени и имя метрики. "
          + "Ряды возвращаются в компактном формате: на каждой панели `series` с метаданными (подпись, единицы) и "
          + "параллельными массивами `t` (epoch millis), `v` (сырые значения), `sv` (масштабированные). "
          + "Опционально `panelsOffset` + `panelsLimit` — срез списка панелей (для подгрузки по скроллу); "
          + "без них возвращаются все панели. Поле `totalChartPanels` — полное число панелей. "
          + "`q` — поиск по названию графика (подстрока, без учёта регистра). "
          + "`maxPoints` — целевой максимум точек на ряд (децимация под отображение); `0` отключает децимацию."
  )
  public CompactMetricsHistoryResponseDto metricsById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Parameter(description = "Начало интервала (ISO-8601), опционально") @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @Parameter(description = "Конец интервала (ISO-8601), опционально") @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
      @Parameter(description = "Имя метрики для фильтра, опционально") @RequestParam(required = false) String metric,
      @Parameter(description = "Поиск по названию графика (подстрока), опционально") @RequestParam(required = false)
      String q,
      @Parameter(description = "Смещение по списку панелей (0-based), опционально") @RequestParam(required = false)
      Integer panelsOffset,
      @Parameter(description = "Максимум панелей в ответе; без параметра — все панели") @RequestParam(required = false)
      Integer panelsLimit,
      @Parameter(description = "Максимум точек на ряд (децимация); 0 — без децимации; без параметра — значение по умолчанию")
      @RequestParam(required = false) Integer maxPoints
  ) {
    int effectiveMaxPoints = maxPoints == null ? DEFAULT_CHART_MAX_POINTS : maxPoints;
    return monitoringService.getMetricsHistoryCompactById(
        deviceId, from, to, metric, q, panelsOffset, panelsLimit, effectiveMaxPoints);
  }

  @PostMapping("/metrics/history-batch")
  @Operation(
      summary = "История метрик батчем",
      description = "Позволяет одним запросом получить историю для нескольких пар deviceId+metricName в компактном формате "
          + "(массивы `t`/`v`/`sv`). Используется дашборд-виджетом GRAPH. `maxPoints` в теле запроса задаёт децимацию."
  )
  public List<CompactMetricsBatchSeriesDto> metricsHistoryBatch(
      @Valid @RequestBody MonitoringMetricsBatchRequest request
  ) {
    return monitoringService.getMetricsWithUnitsBatchCompact(request);
  }

  @GetMapping("/devices/{deviceId}/metrics/latest")
  @Operation(
      summary = "Последние значения метрик по ID устройства",
      description = "По каждой метрике возвращается последняя сохранённая запись с меткой времени и единицами измерения — для табличного представления текущих значений. "
          + "Опциональный фильтр по имени метрики."
  )
  public List<MetricValueDto> latestMetricsById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Parameter(description = "Имя метрики для фильтра, опционально") @RequestParam(required = false) String metric
  ) {
    return monitoringService.getLatestMetricsWithUnitsById(deviceId, metric);
  }

  @GetMapping("/devices/{deviceId}/interfaces")
  @Operation(
      summary = "Интерфейсы устройства по ID",
      description = "Возвращает последний сохранённый снимок интерфейсов устройства. Если снимка ещё нет, читает его по SNMP и сохраняет."
  )
  public List<DeviceInterfaceDto> interfacesById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return monitoringService.getDeviceInterfacesById(deviceId);
  }

  @PostMapping("/devices/{deviceId}/interfaces/refresh")
  @Operation(
      summary = "Обновить интерфейсы устройства по ID",
      description = "Выполняет SNMP-опрос интерфейсов, сохраняет новый снимок в БД и возвращает обновлённые данные."
  )
  public List<DeviceInterfaceDto> refreshInterfacesById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return monitoringService.refreshDeviceInterfacesById(deviceId);
  }

  @GetMapping("/devices/{deviceId}/details")
  @Operation(
      summary = "Детали мониторинга по ID",
      description = "Последний сохранённый snapshot телеметрии устройства (CPU, RAM, uptime, описание и т.д.). "
          + "Если snapshot отсутствует, выполняется первичное чтение."
  )
  public MonitoringDetailsDto detailsById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return monitoringService.getDeviceMonitoringDetailsById(deviceId);
  }

  @PostMapping("/devices/{deviceId}/details/refresh")
  @Operation(
      summary = "Принудительно обновить snapshot телеметрии по ID",
      description = "Выполняет SNMP-опрос, сохраняет новый snapshot и возвращает актуальные значения. "
          + "Параметр live=true помечает, что обновление запрошено из live-режима UI."
  )
  public MonitoringDetailsDto refreshDetailsById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Parameter(description = "Запрос из live-режима frontend") @RequestParam(required = false, defaultValue = "false")
      boolean live
  ) {
    return monitoringService.refreshDeviceMonitoringDetailsById(deviceId, live);
  }

  @GetMapping("/devices/{deviceId}/state/items")
  @Operation(
      summary = "Текущее item state устройства",
      description = "Возвращает страницу актуальных значений item state с фильтром по имени метрики (itemKey / itemDisplayName), valuemap labels и itemDisplayName из шаблона Zabbix."
  )
  public MonitoringItemStatePageDto itemStateById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Parameter(description = "Подстрока в itemKey или itemDisplayName") @RequestParam(required = false) String q,
      @Parameter(description = "Номер страницы (с нуля)") @RequestParam(required = false, defaultValue = "0") int page,
      @Parameter(description = "Размер страницы (1–200; 0 или отсутствие — 20)") @RequestParam(required = false, defaultValue = "0") int size
  ) {
    return monitoringService.getItemStatePage(deviceId, q, page, size);
  }

  @GetMapping("/devices/{deviceId}/state/discovery")
  @Operation(
      summary = "Активные discovery-инстансы устройства",
      description = "Возвращает активные discovery-инстансы и подставленные macros по правилам LLD."
  )
  public List<MonitoringDiscoveryInstanceDto> discoveryStateById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return monitoringService.getDiscoveryStateByDeviceId(deviceId);
  }

  @GetMapping("/devices/{deviceId}/items")
  @Operation(
      summary = "Каталог item устройства и текущий статус мониторинга",
      description = "Возвращает список item из активных шаблонов устройства и флаг active для каждого item."
  )
  public List<MonitoringDeviceItemDto> deviceItemsById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return monitoringService.getDeviceItemsByDeviceId(deviceId);
  }

  @PatchMapping("/devices/{deviceId}/items")
  @Operation(
      summary = "Обновить активные item устройства",
      description = "Сохраняет новый набор активных item для устройства. В запросе передаётся полный список активных item."
  )
  public List<MonitoringDeviceItemDto> updateDeviceItemsById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Valid @RequestBody MonitoringDeviceItemsUpdateRequest request,
      Authentication authentication
  ) {
    return monitoringService.updateDeviceItemsByDeviceId(deviceId, request, authentication);
  }

  @DeleteMapping("/devices/{deviceId}/items/{itemUuid}")
  @Operation(
      summary = "Снять item с мониторинга",
      description = "Удаляет item из активного набора устройства. Для LLD при необходимости можно указать instanceKey."
  )
  public ResponseEntity<Void> deactivateDeviceItemById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Parameter(description = "UUID item из шаблона") @PathVariable String itemUuid,
      @Parameter(description = "Ключ инстанса LLD (опционально)") @RequestParam(required = false) String instanceKey,
      Authentication authentication
  ) {
    monitoringService.deactivateDeviceItemByDeviceId(deviceId, itemUuid, instanceKey, authentication);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/devices/{deviceId}/backups")
  @Operation(summary = "Список бэкапов конфигурации по ID устройства", description = "По IP устройства из записи мониторинга.")
  public DeviceBackupSnapshot listBackupsById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    DeviceScanResult device = monitoringService.getByDeviceId(deviceId);
    return configBackupService.listBackups(device.ip());
  }

  @PostMapping("/devices/{deviceId}/backups/current-as-baseline")
  @Operation(
      summary = "Текущий конфиг как эталон (по ID)",
      description = "Сохраняет актуальную конфигурацию устройства как baseline. Только роль ADMIN."
  )
  public DeviceBackupSnapshot setCurrentAsBaselineById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
  ) {
    return configBackupService.setCurrentAsBaseline(monitoringService.getByDeviceId(deviceId));
  }

  @PostMapping("/devices/{deviceId}/backups/baseline/upload")
  @Operation(
      summary = "Загрузить эталон из файла (по ID)",
      description = "Принимает имя файла и содержимое конфигурации как новый baseline. Только роль ADMIN."
  )
  public DeviceBackupSnapshot uploadBaselineById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Valid @RequestBody BackupBaselineUploadRequest request
  ) {
    DeviceScanResult device = monitoringService.getByDeviceId(deviceId);
    return configBackupService.uploadBaseline(device.ip(), request.fileName(), request.content());
  }

  @PostMapping("/devices/{deviceId}/backups/baseline/select")
  @Operation(
      summary = "Назначить эталоном существующий бэкап (по ID)",
      description = "Выбирает backupId из списка бэкапов устройства. Только роль ADMIN."
  )
  public DeviceBackupSnapshot selectBackupAsBaselineById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Valid @RequestBody BackupSelectionRequest request
  ) {
    DeviceScanResult device = monitoringService.getByDeviceId(deviceId);
    return configBackupService.setBackupAsBaseline(device.ip(), request.backupId());
  }

  @GetMapping("/devices/{deviceId}/backups/{backupId}/compare")
  @Operation(
      summary = "Сравнить бэкап с эталоном (по ID устройства)",
      description = "Возвращает отличия между указанным бэкапом и текущим baseline."
  )
  public BackupComparisonResult compareBackupById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Parameter(description = "Идентификатор бэкапа в хранилище") @PathVariable String backupId
  ) {
    DeviceScanResult device = monitoringService.getByDeviceId(deviceId);
    return configBackupService.compareBackup(device.ip(), backupId);
  }

  @GetMapping("/devices/{deviceId}/backups/{backupId}/download")
  @Operation(
      summary = "Скачать текст бэкапа (по ID устройства)",
      description = "Ответ text/plain с заголовком Content-Disposition attachment."
  )
  public ResponseEntity<String> downloadBackupById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
      @Parameter(description = "Идентификатор бэкапа") @PathVariable String backupId
  ) {
    DeviceScanResult device = monitoringService.getByDeviceId(deviceId);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + backupId + ".cfg\"")
        .contentType(MediaType.TEXT_PLAIN)
        .body(configBackupService.downloadBackup(device.ip(), backupId));
  }

  @GetMapping("/{ip}/metrics")
  @Operation(
      summary = "История метрик по IP (устаревший путь)",
      description = "То же, что метрики по ID, но устройство задаётся IP-адресом в пути. Параметры from, to, metric — опционально."
  )
  public List<MetricValueDto> metrics(
      @Parameter(description = "IPv4/IPv6 устройства на мониторинге") @PathVariable String ip,
      @Parameter(description = "Начало интервала (ISO-8601)") @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
      @Parameter(description = "Конец интервала (ISO-8601)") @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
      @Parameter(description = "Имя метрики") @RequestParam(required = false) String metric
  ) {
    return monitoringService.getMetricsWithUnits(ip, from, to, metric);
  }

  @GetMapping("/{ip}/metrics/latest")
  @Operation(
      summary = "Последние значения метрик по IP (устаревший путь)",
      description = "То же, что последние метрики по ID устройства, но устройство задаётся IP в пути. Параметр metric — опционально."
  )
  public List<MetricValueDto> latestMetrics(
      @Parameter(description = "IPv4/IPv6 устройства на мониторинге") @PathVariable String ip,
      @Parameter(description = "Имя метрики") @RequestParam(required = false) String metric
  ) {
    return monitoringService.getLatestMetricsWithUnits(ip, metric);
  }

  @GetMapping("/{ip}/backups")
  @Operation(summary = "Список бэкапов по IP", description = "Устаревший путь; устройство должно быть на мониторинге.")
  public DeviceBackupSnapshot listBackups(
      @Parameter(description = "IP устройства") @PathVariable String ip
  ) {
    monitoringService.getByIp(ip);
    return configBackupService.listBackups(ip);
  }

  @GetMapping("/{ip}/interfaces")
  @Operation(summary = "Интерфейсы по IP", description = "Устаревший путь вместо /devices/{deviceId}/interfaces.")
  public List<DeviceInterfaceDto> interfaces(
      @Parameter(description = "IP устройства") @PathVariable String ip
  ) {
    return monitoringService.getDeviceInterfaces(ip);
  }

  @GetMapping("/{ip}/details")
  @Operation(summary = "Детали мониторинга по IP", description = "Устаревший путь вместо /devices/{deviceId}/details.")
  public MonitoringDetailsDto details(
      @Parameter(description = "IP устройства") @PathVariable String ip
  ) {
    return monitoringService.getDeviceMonitoringDetails(ip);
  }

  @PostMapping("/{ip}/details/refresh")
  @Operation(summary = "Обновить snapshot телеметрии по IP", description = "Устаревший путь вместо /devices/{deviceId}/details/refresh.")
  public MonitoringDetailsDto refreshDetails(
      @Parameter(description = "IP устройства") @PathVariable String ip,
      @Parameter(description = "Запрос из live-режима frontend") @RequestParam(required = false, defaultValue = "false")
      boolean live
  ) {
    return monitoringService.refreshDeviceMonitoringDetails(ip, live);
  }

  @PostMapping("/{ip}/backups/current-as-baseline")
  @Operation(summary = "Текущий конфиг как эталон (по IP)", description = "Только ADMIN. Устаревший путь.")
  public DeviceBackupSnapshot setCurrentAsBaseline(
      @Parameter(description = "IP устройства") @PathVariable String ip
  ) {
    return configBackupService.setCurrentAsBaseline(monitoringService.getByIp(ip));
  }

  @PostMapping("/{ip}/backups/baseline/upload")
  @Operation(summary = "Загрузить эталон (по IP)", description = "Только ADMIN. Устаревший путь.")
  public DeviceBackupSnapshot uploadBaseline(
      @Parameter(description = "IP устройства") @PathVariable String ip,
      @Valid @RequestBody BackupBaselineUploadRequest request
  ) {
    monitoringService.getByIp(ip);
    return configBackupService.uploadBaseline(ip, request.fileName(), request.content());
  }

  @PostMapping("/{ip}/backups/baseline/select")
  @Operation(summary = "Эталон из бэкапа (по IP)", description = "Только ADMIN. Устаревший путь.")
  public DeviceBackupSnapshot selectBackupAsBaseline(
      @Parameter(description = "IP устройства") @PathVariable String ip,
      @Valid @RequestBody BackupSelectionRequest request
  ) {
    monitoringService.getByIp(ip);
    return configBackupService.setBackupAsBaseline(ip, request.backupId());
  }

  @GetMapping("/{ip}/backups/{backupId}/compare")
  @Operation(summary = "Сравнение бэкапа с эталоном (по IP)", description = "Устаревший путь.")
  public BackupComparisonResult compareBackup(
      @Parameter(description = "IP устройства") @PathVariable String ip,
      @Parameter(description = "Идентификатор бэкапа") @PathVariable String backupId
  ) {
    monitoringService.getByIp(ip);
    return configBackupService.compareBackup(ip, backupId);
  }

  @GetMapping("/{ip}/backups/{backupId}/download")
  @Operation(summary = "Скачать бэкап (по IP)", description = "Устаревший путь; ответ text/plain.")
  public ResponseEntity<String> downloadBackup(
      @Parameter(description = "IP устройства") @PathVariable String ip,
      @Parameter(description = "Идентификатор бэкапа") @PathVariable String backupId
  ) {
    monitoringService.getByIp(ip);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + backupId + ".cfg\"")
        .contentType(MediaType.TEXT_PLAIN)
        .body(configBackupService.downloadBackup(ip, backupId));
  }
}
