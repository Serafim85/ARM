package com.networkscanner.backend.dashboards.api;

import com.networkscanner.backend.dashboards.dto.WidgetCreateRequest;
import com.networkscanner.backend.dashboards.dto.WidgetDto;
import com.networkscanner.backend.dashboards.dto.WidgetUpdateRequest;
import org.springframework.security.core.Authentication;

public interface WidgetService {

  WidgetDto create(Long dashboardId, WidgetCreateRequest request, Authentication authentication);

  WidgetDto update(Long dashboardId, Long widgetId, WidgetUpdateRequest request, Authentication authentication);

  void delete(Long dashboardId, Long widgetId, Authentication authentication);
}
