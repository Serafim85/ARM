package com.networkscanner.backend.network.scan.web;

import com.networkscanner.backend.network.scan.api.ScanRunService;
import com.networkscanner.backend.network.scan.dto.DeviceScanResult;
import com.networkscanner.backend.network.scan.dto.ScanRequest;
import com.networkscanner.backend.network.scan.dto.ScanRunDto;
import com.networkscanner.backend.network.scan.dto.ScanRunStartResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/scan/runs")
@Tag(name = "Сканирование сети", description = "Асинхронные запуски сканирования (роли ADMIN, OPERATOR)")
public class ScanRunController {

  private final ScanRunService scanRunService;

  public ScanRunController(ScanRunService scanRunService) {
    this.scanRunService = scanRunService;
  }

  @PostMapping
  @ResponseStatus(HttpStatus.ACCEPTED)
  @Operation(summary = "Запустить асинхронное сканирование")
  public ScanRunStartResponse start(@Valid @RequestBody ScanRequest request) {
    return scanRunService.startManual(request);
  }

  @GetMapping("/{id}")
  @Operation(summary = "Статус запуска сканирования")
  public ScanRunDto status(@PathVariable long id) {
    return scanRunService.getStatus(id);
  }

  @GetMapping("/{id}/results")
  @Operation(summary = "Результаты успешного сканирования")
  public List<DeviceScanResult> results(@PathVariable long id) {
    return scanRunService.getResults(id);
  }

  @PostMapping("/{id}/stop")
  @Operation(summary = "Остановить выполняющееся сканирование")
  public Map<String, String> stop(@PathVariable long id) {
    boolean stopped = scanRunService.stop(id);
    if (stopped) {
      return Map.of("message", "Остановка сканирования запрошена.");
    }
    return Map.of("message", "Активное сканирование не найдено.");
  }
}
