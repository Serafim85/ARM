package com.networkscanner.backend.users.web;

import com.networkscanner.backend.users.api.CurrentUserPreferencesService;
import com.networkscanner.backend.users.dto.ChartUiPreferencesDto;
import com.networkscanner.backend.users.dto.DefaultDashboardPreferenceDto;
import com.networkscanner.backend.users.dto.DefaultTopologyPreferenceDto;
import com.networkscanner.backend.users.dto.MonitoringDevicesColumnsPreferenceDto;
import com.networkscanner.backend.users.dto.MonitoringEventsColumnsPreferenceDto;
import com.networkscanner.backend.users.dto.TableColumnWidthsPreferenceDto;
import com.networkscanner.backend.users.dto.UpdateChartUiPreferencesRequest;
import com.networkscanner.backend.users.dto.UpdateDefaultDashboardRequest;
import com.networkscanner.backend.users.dto.UpdateDefaultTopologyRequest;
import com.networkscanner.backend.users.dto.UpdateMonitoringDevicesColumnsRequest;
import com.networkscanner.backend.users.dto.UpdateMonitoringEventsColumnsRequest;
import com.networkscanner.backend.users.dto.UpdateTableColumnWidthsRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@Tag(name = "Профиль пользователя", description = "Настройки текущего пользователя.")
public class CurrentUserPreferencesController {

  private final CurrentUserPreferencesService currentUserPreferencesService;

  public CurrentUserPreferencesController(CurrentUserPreferencesService currentUserPreferencesService) {
    this.currentUserPreferencesService = currentUserPreferencesService;
  }

  @PatchMapping("/default-dashboard")
  @Operation(
      summary = "Обновить дашборд по умолчанию",
      description = "Сохраняет defaultDashboardId для текущего пользователя. null снимает настройку."
  )
  public DefaultDashboardPreferenceDto updateDefaultDashboard(
      @RequestBody UpdateDefaultDashboardRequest request,
      Authentication authentication
  ) {
    return currentUserPreferencesService.updateDefaultDashboard(request.defaultDashboardId(), authentication);
  }

  @PatchMapping("/default-topology")
  @Operation(
      summary = "Обновить топологию по умолчанию",
      description = "Сохраняет defaultTopologyId для текущего пользователя. null снимает настройку."
  )
  public DefaultTopologyPreferenceDto updateDefaultTopology(
      @RequestBody UpdateDefaultTopologyRequest request,
      Authentication authentication
  ) {
    return currentUserPreferencesService.updateDefaultTopology(request.defaultTopologyId(), authentication);
  }

  @GetMapping("/monitoring-events-columns")
  @Operation(
      summary = "Настройки колонок таблицы событий",
      description = "Возвращает сохранённый порядок и видимость колонок для текущего пользователя."
  )
  public MonitoringEventsColumnsPreferenceDto getMonitoringEventsColumns(Authentication authentication) {
    return currentUserPreferencesService.getMonitoringEventsColumns(authentication);
  }

  @PatchMapping("/monitoring-events-columns")
  @Operation(
      summary = "Обновить настройки колонок таблицы событий",
      description = "Сохраняет порядок и видимость колонок для текущего пользователя."
  )
  public MonitoringEventsColumnsPreferenceDto updateMonitoringEventsColumns(
      @RequestBody UpdateMonitoringEventsColumnsRequest request,
      Authentication authentication
  ) {
    return currentUserPreferencesService.updateMonitoringEventsColumns(request, authentication);
  }

  @GetMapping("/monitoring-devices-columns")
  @Operation(
      summary = "Настройки колонок таблицы устройств",
      description = "Возвращает сохранённый порядок и видимость колонок для текущего пользователя."
  )
  public MonitoringDevicesColumnsPreferenceDto getMonitoringDevicesColumns(Authentication authentication) {
    return currentUserPreferencesService.getMonitoringDevicesColumns(authentication);
  }

  @PatchMapping("/monitoring-devices-columns")
  @Operation(
      summary = "Обновить настройки колонок таблицы устройств",
      description = "Сохраняет порядок и видимость колонок для текущего пользователя."
  )
  public MonitoringDevicesColumnsPreferenceDto updateMonitoringDevicesColumns(
      @RequestBody UpdateMonitoringDevicesColumnsRequest request,
      Authentication authentication
  ) {
    return currentUserPreferencesService.updateMonitoringDevicesColumns(request, authentication);
  }

  @GetMapping("/chart-ui-preferences")
  @Operation(
      summary = "Настройки легенды графиков",
      description = "Возвращает расположение легенды с таблицей статистики для текущего пользователя."
  )
  public ChartUiPreferencesDto getChartUiPreferences(Authentication authentication) {
    return currentUserPreferencesService.getChartUiPreferences(authentication);
  }

  @PatchMapping("/chart-ui-preferences")
  @Operation(
      summary = "Обновить настройки легенды графиков",
      description = "Сохраняет расположение легенды с таблицей статистики для текущего пользователя."
  )
  public ChartUiPreferencesDto updateChartUiPreferences(
      @RequestBody UpdateChartUiPreferencesRequest request,
      Authentication authentication
  ) {
    return currentUserPreferencesService.updateChartUiPreferences(request, authentication);
  }

  @GetMapping("/table-column-widths")
  @Operation(
      summary = "Ширины колонок таблиц",
      description = "Возвращает сохранённые ширины колонок для таблиц текущего пользователя."
  )
  public TableColumnWidthsPreferenceDto getTableColumnWidths(Authentication authentication) {
    return currentUserPreferencesService.getTableColumnWidths(authentication);
  }

  @PatchMapping("/table-column-widths")
  @Operation(
      summary = "Обновить ширины колонок таблицы",
      description = "Сохраняет ширины колонок для указанной таблицы. Пустая карта сбрасывает настройки таблицы."
  )
  public TableColumnWidthsPreferenceDto updateTableColumnWidths(
      @RequestBody UpdateTableColumnWidthsRequest request,
      Authentication authentication
  ) {
    return currentUserPreferencesService.updateTableColumnWidths(request, authentication);
  }
}
