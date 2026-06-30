package com.networkscanner.backend.users.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.dashboards.api.DashboardService;
import com.networkscanner.backend.topology.api.TopologyService;
import com.networkscanner.backend.users.api.CurrentUserPreferencesService;
import com.networkscanner.backend.users.dto.ChartUiPreferencesDto;
import com.networkscanner.backend.users.dto.DefaultDashboardPreferenceDto;
import com.networkscanner.backend.users.dto.DefaultTopologyPreferenceDto;
import com.networkscanner.backend.users.dto.MonitoringDevicesColumnPreferenceItemDto;
import com.networkscanner.backend.users.dto.MonitoringDevicesColumnsPreferenceDto;
import com.networkscanner.backend.users.dto.MonitoringEventsColumnPreferenceItemDto;
import com.networkscanner.backend.users.dto.MonitoringEventsColumnsPreferenceDto;
import com.networkscanner.backend.users.dto.TableColumnWidthsPreferenceDto;
import com.networkscanner.backend.users.dto.UpdateChartUiPreferencesRequest;
import com.networkscanner.backend.users.dto.UpdateMonitoringDevicesColumnsRequest;
import com.networkscanner.backend.users.dto.UpdateMonitoringEventsColumnsRequest;
import com.networkscanner.backend.users.dto.UpdateTableColumnWidthsRequest;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.repository.AppUserRepository;
import com.networkscanner.backend.users.util.ChartBaseColorValues;
import com.networkscanner.backend.users.util.ChartLegendPlacementIds;
import com.networkscanner.backend.users.util.ChartMetricsCustomDateValues;
import com.networkscanner.backend.users.util.ChartMetricsLayoutIds;
import com.networkscanner.backend.users.util.ChartMetricsPeriodIds;
import com.networkscanner.backend.users.util.MonitoringDevicesColumnIds;
import com.networkscanner.backend.users.util.MonitoringEventsColumnIds;
import com.networkscanner.backend.users.util.TableColumnWidthTableKeys;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CurrentUserPreferencesServiceImpl implements CurrentUserPreferencesService {

  private static final TypeReference<List<MonitoringEventsColumnPreferenceItemDto>> COLUMNS_TYPE =
      new TypeReference<>() {
      };

  private static final TypeReference<List<MonitoringDevicesColumnPreferenceItemDto>> DEVICES_COLUMNS_TYPE =
      new TypeReference<>() {
      };

  private static final TypeReference<Map<String, Object>> CHART_UI_PREFERENCES_TYPE =
      new TypeReference<>() {
      };

  private static final TypeReference<Map<String, Map<String, Integer>>> TABLE_COLUMN_WIDTHS_TYPE =
      new TypeReference<>() {
      };

  private final AppUserRepository appUserRepository;
  private final DashboardService dashboardService;
  private final TopologyService topologyService;
  private final ObjectMapper objectMapper;

  public CurrentUserPreferencesServiceImpl(
      AppUserRepository appUserRepository,
      DashboardService dashboardService,
      TopologyService topologyService,
      ObjectMapper objectMapper
  ) {
    this.appUserRepository = appUserRepository;
    this.dashboardService = dashboardService;
    this.topologyService = topologyService;
    this.objectMapper = objectMapper;
  }

  @Override
  @Transactional
  public DefaultDashboardPreferenceDto updateDefaultDashboard(Long defaultDashboardId, Authentication authentication) {
    AppUser user = requireCurrentUser(authentication);
    if (defaultDashboardId == null) {
      user.setDefaultDashboardId(null);
      return new DefaultDashboardPreferenceDto(null);
    }
    boolean admin = isAdmin(authentication);
    if (!dashboardService.isReadableByUser(defaultDashboardId, user.getId(), admin)) {
      throw new IllegalArgumentException("Нельзя выбрать недоступный или удалённый дашборд.");
    }
    user.setDefaultDashboardId(defaultDashboardId);
    return new DefaultDashboardPreferenceDto(defaultDashboardId);
  }

  @Override
  @Transactional
  public DefaultTopologyPreferenceDto updateDefaultTopology(Long defaultTopologyId, Authentication authentication) {
    AppUser user = requireCurrentUser(authentication);
    if (defaultTopologyId == null) {
      user.setDefaultTopologyId(null);
      return new DefaultTopologyPreferenceDto(null);
    }
    boolean admin = isAdmin(authentication);
    if (!topologyService.isReadableByUser(defaultTopologyId, user.getId(), admin)) {
      throw new IllegalArgumentException("Нельзя выбрать недоступную или удалённую топологию.");
    }
    user.setDefaultTopologyId(defaultTopologyId);
    return new DefaultTopologyPreferenceDto(defaultTopologyId);
  }

  @Override
  @Transactional(readOnly = true)
  public MonitoringEventsColumnsPreferenceDto getMonitoringEventsColumns(Authentication authentication) {
    AppUser user = requireCurrentUser(authentication);
    return readColumnsPreference(user.getMonitoringEventsColumnsJson());
  }

  @Override
  @Transactional
  public MonitoringEventsColumnsPreferenceDto updateMonitoringEventsColumns(
      UpdateMonitoringEventsColumnsRequest request,
      Authentication authentication
  ) {
    AppUser user = requireCurrentUser(authentication);
    List<MonitoringEventsColumnPreferenceItemDto> columns = validateColumnsRequest(request);
    try {
      user.setMonitoringEventsColumnsJson(objectMapper.writeValueAsString(columns));
    } catch (Exception e) {
      throw new IllegalArgumentException("Не удалось сохранить настройки колонок.");
    }
    appUserRepository.save(user);
    return new MonitoringEventsColumnsPreferenceDto(columns);
  }

  @Override
  @Transactional(readOnly = true)
  public MonitoringDevicesColumnsPreferenceDto getMonitoringDevicesColumns(Authentication authentication) {
    AppUser user = requireCurrentUser(authentication);
    return readDevicesColumnsPreference(user.getMonitoringDevicesColumnsJson());
  }

  @Override
  @Transactional
  public MonitoringDevicesColumnsPreferenceDto updateMonitoringDevicesColumns(
      UpdateMonitoringDevicesColumnsRequest request,
      Authentication authentication
  ) {
    AppUser user = requireCurrentUser(authentication);
    List<MonitoringDevicesColumnPreferenceItemDto> columns = validateDevicesColumnsRequest(request);
    try {
      user.setMonitoringDevicesColumnsJson(objectMapper.writeValueAsString(columns));
    } catch (Exception e) {
      throw new IllegalArgumentException("Не удалось сохранить настройки колонок.");
    }
    appUserRepository.save(user);
    return new MonitoringDevicesColumnsPreferenceDto(columns);
  }

  @Override
  @Transactional(readOnly = true)
  public ChartUiPreferencesDto getChartUiPreferences(Authentication authentication) {
    AppUser user = requireCurrentUser(authentication);
    return readChartUiPreferences(user.getChartUiPreferencesJson());
  }

  @Override
  @Transactional
  public ChartUiPreferencesDto updateChartUiPreferences(
      UpdateChartUiPreferencesRequest request,
      Authentication authentication
  ) {
    AppUser user = requireCurrentUser(authentication);
    ChartUiPreferencesDto current = readChartUiPreferences(user.getChartUiPreferencesJson());
    ChartUiPreferencesDto merged = mergeChartUiPreferences(current, request);
    try {
      Map<String, Object> payload = new LinkedHashMap<>();
      payload.put("deviceMetricsLegendPlacement", merged.deviceMetricsLegendPlacement());
      payload.put("deviceMetricsBaseColor", merged.deviceMetricsBaseColor());
      payload.put("dashboardGraphLegendPlacements", merged.dashboardGraphLegendPlacements());
      payload.put("deviceMetricsPeriod", merged.deviceMetricsPeriod());
      payload.put("deviceMetricsLayout", merged.deviceMetricsLayout());
      payload.put("deviceMetricsCustomFrom", merged.deviceMetricsCustomFrom());
      payload.put("deviceMetricsCustomTo", merged.deviceMetricsCustomTo());
      user.setChartUiPreferencesJson(objectMapper.writeValueAsString(payload));
    } catch (Exception e) {
      throw new IllegalArgumentException("Не удалось сохранить настройки графиков.");
    }
    appUserRepository.save(user);
    return merged;
  }

  @Override
  @Transactional(readOnly = true)
  public TableColumnWidthsPreferenceDto getTableColumnWidths(Authentication authentication) {
    AppUser user = requireCurrentUser(authentication);
    return readTableColumnWidths(user.getTableColumnWidthsJson());
  }

  @Override
  @Transactional
  public TableColumnWidthsPreferenceDto updateTableColumnWidths(
      UpdateTableColumnWidthsRequest request,
      Authentication authentication
  ) {
    AppUser user = requireCurrentUser(authentication);
    String tableKey = validateTableColumnWidthsRequest(request);
    Map<String, Map<String, Integer>> current = readTableColumnWidthsMap(user.getTableColumnWidthsJson());
    Map<String, Map<String, Integer>> next = new LinkedHashMap<>(current);
    Map<String, Integer> normalized = normalizeColumnWidths(request.widths());
    if (normalized.isEmpty()) {
      next.remove(tableKey);
    } else {
      next.put(tableKey, normalized);
    }
    try {
      user.setTableColumnWidthsJson(objectMapper.writeValueAsString(next));
    } catch (Exception e) {
      throw new IllegalArgumentException("Не удалось сохранить ширины колонок.");
    }
    appUserRepository.save(user);
    return new TableColumnWidthsPreferenceDto(Map.copyOf(next));
  }

  private MonitoringEventsColumnsPreferenceDto readColumnsPreference(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) {
      return new MonitoringEventsColumnsPreferenceDto(null);
    }
    try {
      List<MonitoringEventsColumnPreferenceItemDto> columns =
          objectMapper.readValue(rawJson, COLUMNS_TYPE);
      return new MonitoringEventsColumnsPreferenceDto(validateColumns(columns));
    } catch (IllegalArgumentException e) {
      return new MonitoringEventsColumnsPreferenceDto(null);
    } catch (Exception e) {
      return new MonitoringEventsColumnsPreferenceDto(null);
    }
  }

  private List<MonitoringEventsColumnPreferenceItemDto> validateColumnsRequest(
      UpdateMonitoringEventsColumnsRequest request
  ) {
    if (request == null || request.columns() == null) {
      throw new IllegalArgumentException("Список колонок обязателен.");
    }
    return validateColumns(request.columns());
  }

  private List<MonitoringEventsColumnPreferenceItemDto> validateColumns(
      List<MonitoringEventsColumnPreferenceItemDto> columns
  ) {
    if (columns.size() != MonitoringEventsColumnIds.expectedCount()) {
      throw new IllegalArgumentException("Некорректный список колонок.");
    }
    Set<String> seen = new HashSet<>();
    int visibleCount = 0;
    for (MonitoringEventsColumnPreferenceItemDto item : columns) {
      if (item == null || item.id() == null || item.id().isBlank()) {
        throw new IllegalArgumentException("Некорректный идентификатор колонки.");
      }
      String id = item.id().trim();
      if (!MonitoringEventsColumnIds.isKnown(id)) {
        throw new IllegalArgumentException("Неизвестная колонка: " + id);
      }
      if (!seen.add(id)) {
        throw new IllegalArgumentException("Дублирующаяся колонка: " + id);
      }
      if (item.visible()) {
        visibleCount++;
      }
    }
    if (visibleCount == 0) {
      throw new IllegalArgumentException("Должна быть видима хотя бы одна колонка.");
    }
    if (seen.size() != MonitoringEventsColumnIds.expectedCount()) {
      throw new IllegalArgumentException("Некорректный список колонок.");
    }
    for (String required : MonitoringEventsColumnIds.ALL) {
      if (!seen.contains(required)) {
        throw new IllegalArgumentException("Отсутствует колонка: " + required);
      }
    }
    return List.copyOf(columns);
  }

  private MonitoringDevicesColumnsPreferenceDto readDevicesColumnsPreference(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) {
      return new MonitoringDevicesColumnsPreferenceDto(null);
    }
    try {
      List<MonitoringDevicesColumnPreferenceItemDto> columns =
          objectMapper.readValue(rawJson, DEVICES_COLUMNS_TYPE);
      return new MonitoringDevicesColumnsPreferenceDto(validateDevicesColumns(columns));
    } catch (IllegalArgumentException e) {
      return new MonitoringDevicesColumnsPreferenceDto(null);
    } catch (Exception e) {
      return new MonitoringDevicesColumnsPreferenceDto(null);
    }
  }

  private List<MonitoringDevicesColumnPreferenceItemDto> validateDevicesColumnsRequest(
      UpdateMonitoringDevicesColumnsRequest request
  ) {
    if (request == null || request.columns() == null) {
      throw new IllegalArgumentException("Список колонок обязателен.");
    }
    return validateDevicesColumns(request.columns());
  }

  private List<MonitoringDevicesColumnPreferenceItemDto> validateDevicesColumns(
      List<MonitoringDevicesColumnPreferenceItemDto> columns
  ) {
    if (columns.size() != MonitoringDevicesColumnIds.expectedCount()) {
      throw new IllegalArgumentException("Некорректный список колонок.");
    }
    Set<String> seen = new HashSet<>();
    int visibleCount = 0;
    for (MonitoringDevicesColumnPreferenceItemDto item : columns) {
      if (item == null || item.id() == null || item.id().isBlank()) {
        throw new IllegalArgumentException("Некорректный идентификатор колонки.");
      }
      String id = item.id().trim();
      if (!MonitoringDevicesColumnIds.isKnown(id)) {
        throw new IllegalArgumentException("Неизвестная колонка: " + id);
      }
      if (!seen.add(id)) {
        throw new IllegalArgumentException("Дублирующаяся колонка: " + id);
      }
      if (item.visible()) {
        visibleCount++;
      }
    }
    if (visibleCount == 0) {
      throw new IllegalArgumentException("Должна быть видима хотя бы одна колонка.");
    }
    if (seen.size() != MonitoringDevicesColumnIds.expectedCount()) {
      throw new IllegalArgumentException("Некорректный список колонок.");
    }
    for (String required : MonitoringDevicesColumnIds.ALL) {
      if (!seen.contains(required)) {
        throw new IllegalArgumentException("Отсутствует колонка: " + required);
      }
    }
    return List.copyOf(columns);
  }

  private ChartUiPreferencesDto readChartUiPreferences(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) {
      return defaultChartUiPreferences();
    }
    try {
      Map<String, Object> raw = objectMapper.readValue(rawJson, CHART_UI_PREFERENCES_TYPE);
      return normalizeChartUiPreferences(raw);
    } catch (Exception e) {
      return defaultChartUiPreferences();
    }
  }

  private ChartUiPreferencesDto mergeChartUiPreferences(
      ChartUiPreferencesDto current,
      UpdateChartUiPreferencesRequest request
  ) {
    if (request == null) {
      return current;
    }
    String devicePlacement = request.deviceMetricsLegendPlacement() != null
        ? ChartLegendPlacementIds.normalizeOrDefault(request.deviceMetricsLegendPlacement())
        : current.deviceMetricsLegendPlacement();
    String deviceBaseColor = request.deviceMetricsBaseColor() != null
        ? ChartBaseColorValues.normalizeOrDefault(request.deviceMetricsBaseColor())
        : current.deviceMetricsBaseColor();
    Map<String, String> dashboardPlacements = new LinkedHashMap<>(current.dashboardGraphLegendPlacements());
    if (request.dashboardGraphLegendPlacements() != null) {
      for (Map.Entry<String, String> entry : request.dashboardGraphLegendPlacements().entrySet()) {
        if (entry.getKey() == null || entry.getKey().isBlank()) {
          continue;
        }
        String widgetId = entry.getKey().trim();
        if (entry.getValue() == null || entry.getValue().isBlank()) {
          dashboardPlacements.remove(widgetId);
          continue;
        }
        dashboardPlacements.put(widgetId, ChartLegendPlacementIds.normalizeOrDefault(entry.getValue()));
      }
    }
    String devicePeriod = request.deviceMetricsPeriod() != null
        ? ChartMetricsPeriodIds.normalizeOrDefault(request.deviceMetricsPeriod())
        : current.deviceMetricsPeriod();
    String deviceLayout = request.deviceMetricsLayout() != null
        ? ChartMetricsLayoutIds.normalizeOrDefault(request.deviceMetricsLayout())
        : current.deviceMetricsLayout();
    String customFrom = request.deviceMetricsCustomFrom() != null
        ? ChartMetricsCustomDateValues.normalizeOrNull(request.deviceMetricsCustomFrom())
        : current.deviceMetricsCustomFrom();
    String customTo = request.deviceMetricsCustomTo() != null
        ? ChartMetricsCustomDateValues.normalizeOrNull(request.deviceMetricsCustomTo())
        : current.deviceMetricsCustomTo();
    return new ChartUiPreferencesDto(
        devicePlacement,
        deviceBaseColor,
        Map.copyOf(dashboardPlacements),
        devicePeriod,
        deviceLayout,
        customFrom,
        customTo
    );
  }

  private ChartUiPreferencesDto normalizeChartUiPreferences(Map<String, Object> raw) {
    String devicePlacement = ChartLegendPlacementIds.normalizeOrDefault(
        raw.get("deviceMetricsLegendPlacement") instanceof String s ? s : null
    );
    String deviceBaseColor = ChartBaseColorValues.normalizeOrDefault(
        raw.get("deviceMetricsBaseColor") instanceof String s ? s : null
    );
    Map<String, String> dashboardPlacements = new LinkedHashMap<>();
    Object dashboardRaw = raw.get("dashboardGraphLegendPlacements");
    if (dashboardRaw instanceof Map<?, ?> map) {
      for (Map.Entry<?, ?> entry : map.entrySet()) {
        if (entry.getKey() == null || entry.getValue() == null) {
          continue;
        }
        String widgetId = String.valueOf(entry.getKey()).trim();
        if (widgetId.isBlank()) {
          continue;
        }
        dashboardPlacements.put(widgetId, ChartLegendPlacementIds.normalizeOrDefault(String.valueOf(entry.getValue())));
      }
    }
    String devicePeriod = ChartMetricsPeriodIds.normalizeOrDefault(
        raw.get("deviceMetricsPeriod") instanceof String s ? s : null
    );
    String deviceLayout = ChartMetricsLayoutIds.normalizeOrDefault(
        raw.get("deviceMetricsLayout") instanceof String s ? s : null
    );
    String customFrom = ChartMetricsCustomDateValues.normalizeOrNull(
        raw.get("deviceMetricsCustomFrom") instanceof String s ? s : null
    );
    String customTo = ChartMetricsCustomDateValues.normalizeOrNull(
        raw.get("deviceMetricsCustomTo") instanceof String s ? s : null
    );
    return new ChartUiPreferencesDto(
        devicePlacement,
        deviceBaseColor,
        Map.copyOf(dashboardPlacements),
        devicePeriod,
        deviceLayout,
        customFrom,
        customTo
    );
  }

  private ChartUiPreferencesDto defaultChartUiPreferences() {
    return new ChartUiPreferencesDto(
        ChartLegendPlacementIds.DEFAULT,
        ChartBaseColorValues.DEFAULT,
        Map.of(),
        ChartMetricsPeriodIds.DEFAULT,
        ChartMetricsLayoutIds.DEFAULT,
        null,
        null
    );
  }

  private TableColumnWidthsPreferenceDto readTableColumnWidths(String rawJson) {
    return new TableColumnWidthsPreferenceDto(readTableColumnWidthsMap(rawJson));
  }

  private Map<String, Map<String, Integer>> readTableColumnWidthsMap(String rawJson) {
    if (rawJson == null || rawJson.isBlank()) {
      return Map.of();
    }
    try {
      Map<String, Map<String, Integer>> raw = objectMapper.readValue(rawJson, TABLE_COLUMN_WIDTHS_TYPE);
      if (raw == null || raw.isEmpty()) {
        return Map.of();
      }
      Map<String, Map<String, Integer>> normalized = new LinkedHashMap<>();
      for (Map.Entry<String, Map<String, Integer>> entry : raw.entrySet()) {
        String tableKey = TableColumnWidthTableKeys.normalize(entry.getKey());
        if (tableKey == null) {
          continue;
        }
        Map<String, Integer> widths = normalizeColumnWidths(entry.getValue());
        if (!widths.isEmpty()) {
          normalized.put(tableKey, widths);
        }
      }
      return Map.copyOf(normalized);
    } catch (Exception e) {
      return Map.of();
    }
  }

  private String validateTableColumnWidthsRequest(UpdateTableColumnWidthsRequest request) {
    if (request == null || request.tableKey() == null || request.tableKey().isBlank()) {
      throw new IllegalArgumentException("Ключ таблицы обязателен.");
    }
    String tableKey = TableColumnWidthTableKeys.normalize(request.tableKey());
    if (tableKey == null) {
      throw new IllegalArgumentException("Неизвестная таблица: " + request.tableKey());
    }
    return tableKey;
  }

  private Map<String, Integer> normalizeColumnWidths(Map<String, Integer> widths) {
    if (widths == null || widths.isEmpty()) {
      return Map.of();
    }
    Map<String, Integer> normalized = new LinkedHashMap<>();
    for (Map.Entry<String, Integer> entry : widths.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank() || entry.getValue() == null) {
        continue;
      }
      String columnId = entry.getKey().trim();
      normalized.put(columnId, TableColumnWidthTableKeys.clampWidth(entry.getValue()));
    }
    return Map.copyOf(normalized);
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
