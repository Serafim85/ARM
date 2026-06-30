package com.networkscanner.backend.dashboards.impl;

import com.networkscanner.backend.audit.api.AuditLogService;
import com.networkscanner.backend.audit.model.AuditAction;
import com.networkscanner.backend.audit.model.AuditCategory;
import com.networkscanner.backend.dashboards.api.WidgetService;
import com.networkscanner.backend.dashboards.dto.WidgetCreateRequest;
import com.networkscanner.backend.dashboards.dto.WidgetDto;
import com.networkscanner.backend.dashboards.dto.WidgetFieldDto;
import com.networkscanner.backend.dashboards.dto.WidgetFieldUpsertRequest;
import com.networkscanner.backend.dashboards.dto.WidgetUpdateRequest;
import com.networkscanner.backend.dashboards.model.AbstractWidgetEntity;
import com.networkscanner.backend.dashboards.model.ClockWidgetEntity;
import com.networkscanner.backend.dashboards.model.DashboardEntity;
import com.networkscanner.backend.dashboards.model.DashboardWidgetFieldEntity;
import com.networkscanner.backend.dashboards.model.GraphWidgetEntity;
import com.networkscanner.backend.dashboards.model.ProblemsWidgetEntity;
import com.networkscanner.backend.dashboards.repository.DashboardRepository;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WidgetServiceImpl implements WidgetService {

  private static final Set<String> CLOCK_FIELDS = Set.of(
      "time_type",
      "clock_type",
      "show_date",
      "show_time",
      "show_time_zone",
      "item_ref",
      "show_seconds",
      "time_format",
      "time_zone",
      "time_zone_format",
      "background_color"
  );
  private static final Set<String> PROBLEMS_FIELDS = Set.of(
      "show",
      "device_ids",
      "device_tags",
      "host_group_refs",
      "host_refs",
      "problem",
      "severity_min",
      "severity_max",
      "show_tags",
      "tag_filters",
      "show_timeline",
      "highlight_row",
      "show_lines",
      "show_suppressed",
      "sort_by",
      "sort_order"
  );
  private static final Set<String> GRAPH_FIELDS = Set.of(
      "period",
      "series",
      "show_legend",
      "legend_placement",
      "fill"
  );

  private final DashboardRepository dashboardRepository;
  private final AppUserRepository appUserRepository;
  private final AuditLogService auditLogService;

  public WidgetServiceImpl(
      DashboardRepository dashboardRepository,
      AppUserRepository appUserRepository,
      AuditLogService auditLogService
  ) {
    this.dashboardRepository = dashboardRepository;
    this.appUserRepository = appUserRepository;
    this.auditLogService = auditLogService;
  }

  @Override
  @Transactional
  public WidgetDto create(Long dashboardId, WidgetCreateRequest request, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    DashboardEntity dashboard = requireDashboardWithModifyAccess(dashboardId, actor.getId(), admin);
    AbstractWidgetEntity widget = newWidgetByType(request.widgetType());
    int nextSortOrder = dashboard.getWidgets().stream().mapToInt(AbstractWidgetEntity::getSortOrder).max().orElse(-1) + 1;
    widget.setSortOrder(nextSortOrder);
    applyCommonFields(widget, request.name(), request.gridX(), request.gridY(), request.width(), request.height(),
        request.viewMode(), request.refreshIntervalSeconds(), request.showHeader(), request.borderWidthPx(),
        request.borderColor(), request.fields(), request.widgetType());
    dashboard.addWidget(widget);
    touchDashboard(dashboard);
    dashboardRepository.flush();
    WidgetDto dto = toWidgetDto(widget);
    auditLogService.record(
        authentication,
        AuditCategory.DASHBOARD,
        AuditAction.CREATE,
        "dashboardId=" + dashboardId + ", widgetId=" + dto.id(),
        "Добавлен виджет " + dto.widgetType()
    );
    return dto;
  }

  @Override
  @Transactional
  public WidgetDto update(Long dashboardId, Long widgetId, WidgetUpdateRequest request, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    DashboardEntity dashboard = requireDashboardWithModifyAccess(dashboardId, actor.getId(), admin);
    AbstractWidgetEntity widget = requireWidgetOwnedByDashboard(dashboard, widgetId);
    String actualType = widgetTypeName(widget);
    String requestedType = normalizeWidgetType(request.widgetType());
    if (!actualType.equals(requestedType)) {
      throw new IllegalArgumentException("Изменение типа виджета не поддерживается.");
    }
    applyCommonFields(widget, request.name(), request.gridX(), request.gridY(), request.width(), request.height(),
        request.viewMode(), request.refreshIntervalSeconds(), request.showHeader(), request.borderWidthPx(),
        request.borderColor(), request.fields(), request.widgetType());
    touchDashboard(dashboard);
    WidgetDto dto = toWidgetDto(widget);
    auditLogService.record(
        authentication,
        AuditCategory.DASHBOARD,
        AuditAction.UPDATE,
        "dashboardId=" + dashboardId + ", widgetId=" + widgetId,
        "Изменён виджет " + dto.widgetType()
    );
    return dto;
  }

  @Override
  @Transactional
  public void delete(Long dashboardId, Long widgetId, Authentication authentication) {
    AppUser actor = requireCurrentUser(authentication);
    boolean admin = isAdmin(authentication);
    DashboardEntity dashboard = requireDashboardWithModifyAccess(dashboardId, actor.getId(), admin);
    AbstractWidgetEntity widget = requireWidgetOwnedByDashboard(dashboard, widgetId);
    String wtype = widgetTypeName(widget);
    dashboard.removeWidget(widget);
    touchDashboard(dashboard);
    auditLogService.record(
        authentication,
        AuditCategory.DASHBOARD,
        AuditAction.DELETE,
        "dashboardId=" + dashboardId + ", widgetId=" + widgetId,
        "Удалён виджет " + wtype
    );
  }

  private DashboardEntity requireDashboardWithModifyAccess(Long dashboardId, Long userId, boolean admin) {
    DashboardEntity dashboard = dashboardRepository.findFetchedById(dashboardId)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Дашборд не найден."));
    if (!canModify(dashboard, userId, admin)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Изменять виджеты может только владелец или администратор.");
    }
    return dashboard;
  }

  private static AbstractWidgetEntity requireWidgetOwnedByDashboard(DashboardEntity dashboard, Long widgetId) {
    return dashboard.getWidgets().stream()
        .filter(w -> w.getId().equals(widgetId))
        .findFirst()
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Виджет не найден на указанном дашборде."));
  }

  private static AbstractWidgetEntity newWidgetByType(String widgetType) {
    String type = normalizeWidgetType(widgetType);
    return switch (type) {
      case "CLOCK" -> new ClockWidgetEntity();
      case "PROBLEMS" -> new ProblemsWidgetEntity();
      case "GRAPH" -> new GraphWidgetEntity();
      default -> throw new IllegalArgumentException("Поддерживаются только типы CLOCK, PROBLEMS и GRAPH.");
    };
  }

  private static String normalizeWidgetType(String widgetType) {
    if (widgetType == null || widgetType.isBlank()) {
      throw new IllegalArgumentException("Укажите тип виджета.");
    }
    return widgetType.strip().toUpperCase();
  }

  private static void validateFieldNames(String widgetType, List<WidgetFieldUpsertRequest> fields) {
    Set<String> allowed = switch (normalizeWidgetType(widgetType)) {
      case "CLOCK" -> CLOCK_FIELDS;
      case "PROBLEMS" -> PROBLEMS_FIELDS;
      case "GRAPH" -> GRAPH_FIELDS;
      default -> throw new IllegalArgumentException("Поддерживаются только типы CLOCK, PROBLEMS и GRAPH.");
    };
    for (WidgetFieldUpsertRequest field : fields) {
      String normalized = field.name().strip().toLowerCase();
      if (!allowed.contains(normalized)) {
        throw new IllegalArgumentException(
            "Поле '" + field.name() + "' не поддерживается для виджета типа " + normalizeWidgetType(widgetType) + ".");
      }
    }
  }

  private static void applyCommonFields(
      AbstractWidgetEntity widget,
      String name,
      int gridX,
      int gridY,
      int width,
      int height,
      int viewMode,
      Integer refreshIntervalSeconds,
      boolean showHeader,
      Integer borderWidthPx,
      String borderColor,
      List<WidgetFieldUpsertRequest> fields,
      String widgetType
  ) {
    if (refreshIntervalSeconds != null && refreshIntervalSeconds < 1) {
      throw new IllegalArgumentException("Интервал обновления должен быть больше 0 секунд.");
    }
    int bw = borderWidthPx == null ? 1 : borderWidthPx;
    if (bw < 0 || bw > 32) {
      throw new IllegalArgumentException("Ширина границы должна быть от 0 до 32 пикселей.");
    }
    String bc = borderColor == null || borderColor.isBlank() ? "gray" : borderColor.strip();
    if (bc.length() > 64) {
      throw new IllegalArgumentException("Цвет границы слишком длинный.");
    }
    if (bc.indexOf(';') >= 0 || bc.indexOf('\n') >= 0 || bc.indexOf('\r') >= 0) {
      throw new IllegalArgumentException("Недопустимый цвет границы.");
    }
    validateFieldNames(widgetType, fields);
    widget.setName(name.strip());
    widget.setGridX(gridX);
    widget.setGridY(gridY);
    widget.setWidth(width);
    widget.setHeight(height);
    widget.setViewMode(viewMode);
    widget.setRefreshIntervalSeconds(refreshIntervalSeconds);
    widget.setShowHeader(showHeader);
    widget.setBorderWidthPx(bw);
    widget.setBorderColor(bc);
    widget.clearFields();
    for (WidgetFieldUpsertRequest requestField : fields) {
      DashboardWidgetFieldEntity field = new DashboardWidgetFieldEntity();
      field.setName(requestField.name().strip().toLowerCase());
      field.setValueInt(requestField.valueInt());
      field.setValueStr(requestField.valueStr() == null ? "" : requestField.valueStr());
      widget.addField(field);
    }
  }

  private static void touchDashboard(DashboardEntity dashboard) {
    dashboard.setUpdatedAt(OffsetDateTime.now());
  }

  private static boolean canModify(DashboardEntity dashboard, Long userId, boolean admin) {
    return admin || dashboard.getOwner().getId().equals(userId);
  }

  private static String widgetTypeName(AbstractWidgetEntity widget) {
    if (widget instanceof ClockWidgetEntity) {
      return "CLOCK";
    }
    if (widget instanceof ProblemsWidgetEntity) {
      return "PROBLEMS";
    }
    if (widget instanceof GraphWidgetEntity) {
      return "GRAPH";
    }
    return "PLACEHOLDER";
  }

  private static WidgetDto toWidgetDto(AbstractWidgetEntity widget) {
    List<WidgetFieldDto> fields = widget.getFields().stream()
        .map(field -> new WidgetFieldDto(field.getId(), field.getName(), field.getValueInt(), field.getValueStr()))
        .toList();
    return new WidgetDto(
        widget.getId(),
        widget.getDashboard().getId(),
        widget.getSortOrder(),
        widget.getName(),
        widgetTypeName(widget),
        widget.getGridX(),
        widget.getGridY(),
        widget.getWidth(),
        widget.getHeight(),
        widget.getViewMode(),
        widget.getRefreshIntervalSeconds(),
        widget.isShowHeader(),
        widget.getBorderWidthPx(),
        widget.getBorderColor(),
        fields
    );
  }

  private AppUser requireCurrentUser(Authentication authentication) {
    String email = authentication.getName();
    return appUserRepository.findByEmailIgnoreCase(email)
        .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Пользователь не найден."));
  }

  private static boolean isAdmin(Authentication authentication) {
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .anyMatch("ROLE_ADMIN"::equals);
  }
}
