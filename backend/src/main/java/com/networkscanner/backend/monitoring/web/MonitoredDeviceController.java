package com.networkscanner.backend.monitoring.web;

import com.networkscanner.backend.monitoring.api.MonitoringService;
import com.networkscanner.backend.monitoring.dto.MonitoredDeviceDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/monitoring/monitored-devices")
@Tag(
    name = "Мониторинг: сущность устройства",
    description = "Доступ к полной записи устройства в БД (monitored_devices), включая шаблоны SNMP и метки времени"
)
public class MonitoredDeviceController {

  private final MonitoringService monitoringService;

  public MonitoredDeviceController(MonitoringService monitoringService) {
    this.monitoringService = monitoringService;
  }

  @GetMapping("/{id}")
  @Operation(
      summary = "Устройство по идентификатору записи",
      description = "Возвращает MonitoredDeviceDto по первичному ключу таблицы monitored_devices (не путать с IP-адресом)."
  )
  public MonitoredDeviceDto getById(
      @Parameter(description = "ID записи monitored_devices") @PathVariable Long id
  ) {
    return monitoringService.getMonitoredDeviceById(id);
  }
}
