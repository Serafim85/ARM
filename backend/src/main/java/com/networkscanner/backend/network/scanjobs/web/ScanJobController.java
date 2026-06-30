package com.networkscanner.backend.network.scanjobs.web;

import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.ScanRunStartResponse;
import com.networkscanner.backend.network.scanjobs.api.ScanJobService;
import com.networkscanner.backend.network.scanjobs.dto.DiscoveredNotMonitoredSummaryDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobDetailsDto;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobMetaUpdateRequest;
import com.networkscanner.backend.network.scanjobs.dto.ScanJobUpsertRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scan-jobs")
@Tag(name = "Автосканирование", description = "Задачи фонового сканирования сети по CRON.")
public class ScanJobController {

  private final ScanJobService service;

  public ScanJobController(ScanJobService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(summary = "Список задач автосканирования")
  public List<ScanJobDto> list() {
    return service.list();
  }

  @GetMapping("/discovered-not-monitored-summary")
  @Operation(summary = "Уникальные IP из последних результатов задач, ещё не на мониторинге")
  public DiscoveredNotMonitoredSummaryDto discoveredNotMonitoredSummary() {
    return service.getDiscoveredNotMonitoredSummary();
  }

  @GetMapping("/discovered-not-monitored-devices")
  @Operation(summary = "Список устройств из последних результатов задач, ещё не на мониторинге")
  public List<DeviceScanResult> discoveredNotMonitoredDevices() {
    return service.getDiscoveredNotMonitoredDevices();
  }

  @GetMapping("/{id}")
  @Operation(summary = "Задача автосканирования по ID")
  public ScanJobDto get(@PathVariable long id) {
    return service.get(id);
  }

  @GetMapping("/{id}/details")
  @Operation(summary = "Задача автосканирования по ID (с параметрами сканирования)")
  public ScanJobDetailsDto details(@PathVariable long id) {
    return service.getDetails(id);
  }

  @PostMapping
  @Operation(summary = "Создать задачу автосканирования")
  public ScanJobDto create(
      @Valid @RequestBody ScanJobUpsertRequest request,
      Authentication authentication
  ) {
    return service.create(request, authentication);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Обновить задачу автосканирования")
  public ScanJobDto update(
      @PathVariable long id,
      @Valid @RequestBody ScanJobUpsertRequest request,
      Authentication authentication
  ) {
    return service.update(id, request, authentication);
  }

  @PutMapping("/{id}/meta")
  @Operation(summary = "Обновить имя/расписание/состояние задачи")
  public ScanJobDto updateMeta(
      @PathVariable long id,
      @Valid @RequestBody ScanJobMetaUpdateRequest request,
      Authentication authentication
  ) {
    return service.updateMeta(id, request, authentication);
  }

  @PostMapping("/{id}/enable")
  @Operation(summary = "Включить задачу")
  public ScanJobDto enable(@PathVariable long id, Authentication authentication) {
    return service.setEnabled(id, true, authentication);
  }

  @PostMapping("/{id}/disable")
  @Operation(summary = "Выключить задачу")
  public ScanJobDto disable(@PathVariable long id, Authentication authentication) {
    return service.setEnabled(id, false, authentication);
  }

  @PostMapping("/{id}/run")
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Запустить задачу сейчас (асинхронно)")
  public ScanRunStartResponse runNow(@PathVariable long id) {
    return service.runNow(id);
  }

  @GetMapping("/{id}/last-result")
  @Operation(summary = "Последний результат сканирования по задаче")
  public List<DeviceScanResult> lastResult(@PathVariable long id) {
    return service.getLastResult(id);
  }

  @DeleteMapping("/{id}")
  @Operation(summary = "Удалить задачу автосканирования")
  public void delete(@PathVariable long id, Authentication authentication) {
    service.delete(id, authentication);
  }
}

