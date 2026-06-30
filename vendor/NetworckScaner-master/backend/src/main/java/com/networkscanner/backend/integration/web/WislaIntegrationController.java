package com.networkscanner.backend.integration.web;

import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.api.WislaBootstrapService;
import com.networkscanner.backend.integration.dto.ProbeBootstrapPageResponse;
import com.networkscanner.backend.integration.dto.ProbeBootstrapPayload;
import com.networkscanner.backend.integration.dto.WislaBootstrapRequest;
import com.networkscanner.backend.monitoring.api.MonitoringService;
import com.networkscanner.backend.monitoring.dto.DeviceInterfaceDto;
import com.networkscanner.backend.monitoring.dto.DeviceMetricsHistoryResponseDto;
import com.networkscanner.backend.monitoring.dto.MetricValueDto;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchRequest;
import com.networkscanner.backend.monitoring.dto.MonitoringMetricsBatchSeriesDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/integration/wisla/v1")
@Tag(name = "Интеграция Wisla", description = "API bootstrap/re-sync для интеграции NS -> Wisla")
public class WislaIntegrationController {

  private static final Logger log = LoggerFactory.getLogger(WislaIntegrationController.class);

  private static final String WISLA_BOOTSTRAP_SCHEMA_VERSION = "1.0";
  private static final int DEFAULT_PAGE_SIZE = 100;

  private final WislaBootstrapService wislaBootstrapService;
  private final SourceSystemProvider sourceSystemProvider;
  private final MonitoringService monitoringService;

  public WislaIntegrationController(
      WislaBootstrapService wislaBootstrapService,
      SourceSystemProvider sourceSystemProvider,
      MonitoringService monitoringService
  ) {
    this.wislaBootstrapService = wislaBootstrapService;
    this.sourceSystemProvider = sourceSystemProvider;
    this.monitoringService = monitoringService;
  }

  @GetMapping("/monitored-devices")
  @Operation(summary = "Список устройств на мониторинге для Wisla bootstrap/re-sync")
  public ProbeBootstrapPageResponse monitoredDevices(
      @Parameter(description = "Номер страницы (с нуля)")
      @RequestParam(defaultValue = "0")
      @Min(0) int page,
      @Parameter(description = "Размер страницы (1..500)")
      @RequestParam(defaultValue = "100")
      @Min(1) @Max(500) int size,
      @Parameter(description = "Возвращать устройства, обновленные не ранее указанной даты (ISO-8601)")
      @RequestParam(required = false)
      @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime updatedSince
  ) {
    long startedAtNanos = System.nanoTime();
    WislaBootstrapRequest request = new WislaBootstrapRequest(page, size, updatedSince);
    Page<ProbeBootstrapPayload> result = wislaBootstrapService.listMonitoredDevices(request);
    long durationMs = (System.nanoTime() - startedAtNanos) / 1_000_000L;
    String resolvedSourceSystem = sourceSystemProvider.getSourceSystem();
    log.info(
        "Wisla bootstrap page prepared: sourceSystem={}, page={}, size={}, totalElements={}, durationMs={}",
        resolvedSourceSystem,
        page,
        size,
        result.getTotalElements(),
        durationMs
    );

    return new ProbeBootstrapPageResponse(
        WISLA_BOOTSTRAP_SCHEMA_VERSION,
        resolvedSourceSystem,
        OffsetDateTime.now(),
        result.getNumber(),
        result.getSize(),
        result.getTotalElements(),
        result.getTotalPages(),
        result.isFirst(),
        result.isLast(),
        result.getContent()
    );
  }

    @GetMapping("/monitored-devices/devices/{deviceId}/metrics/latest")
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

    @GetMapping("/monitored-devices/devices/{deviceId}/metrics")
    @Operation(
            summary = "История метрик по ID устройства",
            description = "Выборка метрик из хранилища с подписью единиц измерения. Фильтры: интервал времени и имя метрики. "
                    + "Точки (`points`) вложены в каждую панель `chartPanels`. Опционально `panelsOffset` + `panelsLimit` — срез списка "
                    + "панелей (для подгрузки по скроллу); без них возвращаются все панели. Поле `totalChartPanels` — полное число панелей."
    )
    public DeviceMetricsHistoryResponseDto metricsById(
            @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId,
            @Parameter(description = "Начало интервала (ISO-8601), опционально") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @Parameter(description = "Конец интервала (ISO-8601), опционально") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @Parameter(description = "Имя метрики для фильтра, опционально") @RequestParam(required = false) String metric,
            @Parameter(description = "Смещение по списку панелей (0-based), опционально") @RequestParam(required = false)
            Integer panelsOffset,
            @Parameter(description = "Максимум панелей в ответе; без параметра — все панели") @RequestParam(required = false)
            Integer panelsLimit
    ) {
        return monitoringService.getMetricsHistoryById(deviceId, from, to, metric, null, panelsOffset, panelsLimit, null);
    }

    @GetMapping("/monitored-devices/devices/{deviceId}/interfaces")
    @Operation(
            summary = "Интерфейсы устройства по ID",
            description = "Возвращает последний сохранённый снимок интерфейсов устройства. "
                    + "Если снимка ещё нет, читает его по SNMP и сохраняет. "
                    + "Идентично GET /api/monitoring/devices/{deviceId}/interfaces."
    )
    public List<DeviceInterfaceDto> interfacesById(
            @Parameter(description = "ID записи monitored_devices") @PathVariable Long deviceId
    ) {
        return monitoringService.getDeviceInterfacesById(deviceId);
    }

    @PostMapping("/metrics/history-batch")
    @Operation(
            summary = "История метрик батчем",
            description = "Позволяет одним запросом получить историю для нескольких пар deviceId+metricName. "
                    + "Используется дашборд-виджетом GRAPH."
    )
    public List<MonitoringMetricsBatchSeriesDto> metricsHistoryBatch(
            @Valid @RequestBody MonitoringMetricsBatchRequest request
    ) {
        return monitoringService.getMetricsWithUnitsBatch(request);
    }


}
