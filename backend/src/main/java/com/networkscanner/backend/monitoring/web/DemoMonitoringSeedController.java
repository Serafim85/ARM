package com.networkscanner.backend.monitoring.web;

import com.networkscanner.backend.monitoring.dto.DemoMonitoringSeedResponseDto;
import com.networkscanner.backend.monitoring.impl.DemoMonitoringSeedService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
@Tag(name = "Отладка", description = "Вспомогательные эндпоинты для разработки и демонстрации")
public class DemoMonitoringSeedController {

  private final DemoMonitoringSeedService demoMonitoringSeedService;

  public DemoMonitoringSeedController(DemoMonitoringSeedService demoMonitoringSeedService) {
    this.demoMonitoringSeedService = demoMonitoringSeedService;
  }

  @PostMapping("/demo-monitoring-seed")
  @Operation(summary = "Загрузить демо-данные мониторинга (7 устройств, события, история метрик)")
  public DemoMonitoringSeedResponseDto seedDemoMonitoring() {
    return demoMonitoringSeedService.seedDemoMonitoringData();
  }
}
