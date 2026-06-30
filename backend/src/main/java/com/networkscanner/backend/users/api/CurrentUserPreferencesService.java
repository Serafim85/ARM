package com.networkscanner.backend.users.api;

import com.networkscanner.backend.users.dto.ChartUiPreferencesDto;
import com.networkscanner.backend.users.dto.DefaultDashboardPreferenceDto;
import com.networkscanner.backend.users.dto.DefaultTopologyPreferenceDto;
import com.networkscanner.backend.users.dto.MonitoringDevicesColumnsPreferenceDto;
import com.networkscanner.backend.users.dto.MonitoringEventsColumnsPreferenceDto;
import com.networkscanner.backend.users.dto.TableColumnWidthsPreferenceDto;
import com.networkscanner.backend.users.dto.UpdateChartUiPreferencesRequest;
import com.networkscanner.backend.users.dto.UpdateMonitoringDevicesColumnsRequest;
import com.networkscanner.backend.users.dto.UpdateMonitoringEventsColumnsRequest;
import com.networkscanner.backend.users.dto.UpdateTableColumnWidthsRequest;
import org.springframework.security.core.Authentication;

public interface CurrentUserPreferencesService {

  DefaultDashboardPreferenceDto updateDefaultDashboard(Long defaultDashboardId, Authentication authentication);

  DefaultTopologyPreferenceDto updateDefaultTopology(Long defaultTopologyId, Authentication authentication);

  MonitoringEventsColumnsPreferenceDto getMonitoringEventsColumns(Authentication authentication);

  MonitoringEventsColumnsPreferenceDto updateMonitoringEventsColumns(
      UpdateMonitoringEventsColumnsRequest request,
      Authentication authentication
  );

  MonitoringDevicesColumnsPreferenceDto getMonitoringDevicesColumns(Authentication authentication);

  MonitoringDevicesColumnsPreferenceDto updateMonitoringDevicesColumns(
      UpdateMonitoringDevicesColumnsRequest request,
      Authentication authentication
  );

  ChartUiPreferencesDto getChartUiPreferences(Authentication authentication);

  ChartUiPreferencesDto updateChartUiPreferences(
      UpdateChartUiPreferencesRequest request,
      Authentication authentication
  );

  TableColumnWidthsPreferenceDto getTableColumnWidths(Authentication authentication);

  TableColumnWidthsPreferenceDto updateTableColumnWidths(
      UpdateTableColumnWidthsRequest request,
      Authentication authentication
  );
}
