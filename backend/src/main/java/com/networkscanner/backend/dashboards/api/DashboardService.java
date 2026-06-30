package com.networkscanner.backend.dashboards.api;

import com.networkscanner.backend.dashboards.dto.DashboardCreateRequest;
import com.networkscanner.backend.dashboards.dto.DashboardDto;
import com.networkscanner.backend.dashboards.dto.DashboardUpdateRequest;
import com.networkscanner.backend.dashboards.dto.WidgetPageDto;
import java.util.List;
import org.springframework.security.core.Authentication;

public interface DashboardService {

  List<DashboardDto> listAccessible(Authentication authentication);

  DashboardDto create(DashboardCreateRequest request, Authentication authentication);

  DashboardDto update(Long id, DashboardUpdateRequest request, Authentication authentication);

  void delete(Long id, Authentication authentication);

  DashboardDto getById(Long id, Authentication authentication);

  boolean isReadableByUser(Long dashboardId, Long userId, boolean admin);

  WidgetPageDto listWidgets(
      String search,
      Long dashboardId,
      String widgetType,
      int page,
      int size,
      Authentication authentication
  );
}
