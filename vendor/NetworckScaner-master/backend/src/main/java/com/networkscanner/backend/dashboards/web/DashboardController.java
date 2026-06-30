package com.networkscanner.backend.dashboards.web;

import com.networkscanner.backend.dashboards.api.DashboardService;
import com.networkscanner.backend.dashboards.api.WidgetService;
import com.networkscanner.backend.dashboards.dto.DashboardCreateRequest;
import com.networkscanner.backend.dashboards.dto.DashboardDto;
import com.networkscanner.backend.dashboards.dto.WidgetCreateRequest;
import com.networkscanner.backend.dashboards.dto.WidgetDto;
import com.networkscanner.backend.dashboards.dto.DashboardUpdateRequest;
import com.networkscanner.backend.dashboards.dto.WidgetPageDto;
import com.networkscanner.backend.dashboards.dto.ServerTimeDto;
import com.networkscanner.backend.dashboards.dto.WidgetUpdateRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboards")
@Tag(
    name = "Дашборды",
    description = "Создание и изменение дашбордов (владелец или ADMIN), чтение и список виджетов для доступных дашбордов."
)
public class DashboardController {

  private final DashboardService dashboardService;
  private final WidgetService widgetService;

  public DashboardController(DashboardService dashboardService, WidgetService widgetService) {
    this.dashboardService = dashboardService;
    this.widgetService = widgetService;
  }

  @GetMapping
  @Operation(
      summary = "Список доступных дашбордов",
      description = "Дашборды текущего пользователя (владелец или в списке shared) и все дашборды для ADMIN. "
          + "Без списка виджетов."
  )
  public List<DashboardDto> list(Authentication authentication) {
    return dashboardService.listAccessible(authentication);
  }

  @PostMapping
  @Operation(summary = "Создать дашборд", description = "Владелец — текущий пользователь.")
  public DashboardDto create(
      @Valid @RequestBody DashboardCreateRequest request,
      Authentication authentication
  ) {
    return dashboardService.create(request, authentication);
  }

  @GetMapping("/server-time")
  @Operation(
      summary = "Время сервера",
      description = "Текущий момент по часам сервера приложения (UTC). Для виджета CLOCK в режиме «серверное время»."
  )
  public ServerTimeDto serverTime() {
    return new ServerTimeDto(Instant.now().toEpochMilli());
  }

  @GetMapping("/widgets")
  @Operation(
      summary = "Список виджетов, доступных пользователю",
      description = "Виджеты с дашбордов, к которым у пользователя есть доступ (владелец, shared или ADMIN). "
          + "Поиск по имени виджета (search), фильтр по dashboardId и типу виджета (widgetType: PLACEHOLDER, CLOCK, PROBLEMS, GRAPH). "
          + "Пагинация: page (с 0), size (по умолчанию 20, не более 200)."
  )
  public WidgetPageDto listWidgets(
      @Parameter(description = "Подстрока имени виджета (без учёта регистра)")
      @RequestParam(required = false) String search,
      @Parameter(description = "Оставить только виджеты указанного дашборда")
      @RequestParam(required = false) Long dashboardId,
      @Parameter(description = "Тип виджета (дискриминатор): PLACEHOLDER, CLOCK, PROBLEMS, GRAPH")
      @RequestParam(required = false) String widgetType,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      Authentication authentication
  ) {
    return dashboardService.listWidgets(search, dashboardId, widgetType, page, size, authentication);
  }

  @PostMapping("/{dashboardId:\\d+}/widgets")
  @Operation(summary = "Создать виджет", description = "Поддерживаемые типы: CLOCK, PROBLEMS, GRAPH.")
  public WidgetDto createWidget(
      @Parameter(description = "Идентификатор дашборда") @PathVariable Long dashboardId,
      @Valid @RequestBody WidgetCreateRequest request,
      Authentication authentication
  ) {
    return widgetService.create(dashboardId, request, authentication);
  }

  @PutMapping("/{dashboardId:\\d+}/widgets/{widgetId:\\d+}")
  @Operation(summary = "Обновить виджет", description = "Обновляет layout, общие параметры и поля виджета.")
  public WidgetDto updateWidget(
      @Parameter(description = "Идентификатор дашборда") @PathVariable Long dashboardId,
      @Parameter(description = "Идентификатор виджета") @PathVariable Long widgetId,
      @Valid @RequestBody WidgetUpdateRequest request,
      Authentication authentication
  ) {
    return widgetService.update(dashboardId, widgetId, request, authentication);
  }

  @DeleteMapping("/{dashboardId:\\d+}/widgets/{widgetId:\\d+}")
  @Operation(summary = "Удалить виджет", description = "Удаляет виджет из указанного дашборда.")
  public void deleteWidget(
      @Parameter(description = "Идентификатор дашборда") @PathVariable Long dashboardId,
      @Parameter(description = "Идентификатор виджета") @PathVariable Long widgetId,
      Authentication authentication
  ) {
    widgetService.delete(dashboardId, widgetId, authentication);
  }

  @PutMapping("/{id:\\d+}")
  @Operation(
      summary = "Изменить дашборд",
      description = "Только владелец дашборда или пользователь с ролью ADMIN."
  )
  public DashboardDto update(
      @Parameter(description = "Идентификатор дашборда") @PathVariable Long id,
      @Valid @RequestBody DashboardUpdateRequest request,
      Authentication authentication
  ) {
    return dashboardService.update(id, request, authentication);
  }

  @DeleteMapping("/{id:\\d+}")
  @Operation(
      summary = "Удалить дашборд",
      description = "Только владелец дашборда или пользователь с ролью ADMIN."
  )
  public void delete(
      @Parameter(description = "Идентификатор дашборда") @PathVariable Long id,
      Authentication authentication
  ) {
    dashboardService.delete(id, authentication);
  }

  @GetMapping("/{id:\\d+}")
  @Operation(
      summary = "Получить дашборд по id",
      description = "Включает виджеты. Доступ: владелец, пользователь из списка sharedUserIds или ADMIN."
  )
  public DashboardDto getById(
      @Parameter(description = "Идентификатор дашборда") @PathVariable Long id,
      Authentication authentication
  ) {
    return dashboardService.getById(id, authentication);
  }
}
