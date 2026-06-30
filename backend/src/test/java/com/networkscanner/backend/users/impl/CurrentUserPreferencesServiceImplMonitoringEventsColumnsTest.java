package com.networkscanner.backend.users.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.dashboards.api.DashboardService;
import com.networkscanner.backend.topology.api.TopologyService;
import com.networkscanner.backend.users.dto.MonitoringEventsColumnPreferenceItemDto;
import com.networkscanner.backend.users.dto.UpdateMonitoringEventsColumnsRequest;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.repository.AppUserRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CurrentUserPreferencesServiceImplMonitoringEventsColumnsTest {

  @Test
  void updateMonitoringEventsColumns_persistsValidPreference() throws Exception {
    AppUserRepository repository = mock(AppUserRepository.class);
    AppUser user = new AppUser();
    user.setEmail("user@example.com");
    when(repository.findByEmailIgnoreCase("user@example.com")).thenReturn(java.util.Optional.of(user));
    when(repository.save(any(AppUser.class))).thenAnswer(inv -> inv.getArgument(0));

    CurrentUserPreferencesServiceImpl service = new CurrentUserPreferencesServiceImpl(
        repository,
        mock(DashboardService.class),
        mock(TopologyService.class),
        new ObjectMapper()
    );

    var auth = new UsernamePasswordAuthenticationToken("user@example.com", "n/a");
    var columns = List.of(
        new MonitoringEventsColumnPreferenceItemDto("breachStartedAt", true),
        new MonitoringEventsColumnPreferenceItemDto("duration", true),
        new MonitoringEventsColumnPreferenceItemDto("thresholdLevel", true),
        new MonitoringEventsColumnPreferenceItemDto("status", true),
        new MonitoringEventsColumnPreferenceItemDto("deviceHostName", false),
        new MonitoringEventsColumnPreferenceItemDto("deviceName", true),
        new MonitoringEventsColumnPreferenceItemDto("metricName", true),
        new MonitoringEventsColumnPreferenceItemDto("thresholdValue", true),
        new MonitoringEventsColumnPreferenceItemDto("actualValue", true)
    );

    var saved = service.updateMonitoringEventsColumns(new UpdateMonitoringEventsColumnsRequest(columns), auth);

    assertEquals(columns, saved.columns());
    verify(repository).save(user);
    assertEquals(new ObjectMapper().writeValueAsString(columns), user.getMonitoringEventsColumnsJson());
  }

  @Test
  void getMonitoringEventsColumns_returnsNullWhenUnset() {
    AppUserRepository repository = mock(AppUserRepository.class);
    AppUser user = new AppUser();
    user.setEmail("user@example.com");
    when(repository.findByEmailIgnoreCase("user@example.com")).thenReturn(java.util.Optional.of(user));

    CurrentUserPreferencesServiceImpl service = new CurrentUserPreferencesServiceImpl(
        repository,
        mock(DashboardService.class),
        mock(TopologyService.class),
        new ObjectMapper()
    );

    var auth = new UsernamePasswordAuthenticationToken("user@example.com", "n/a");
    assertNull(service.getMonitoringEventsColumns(auth).columns());
  }

  @Test
  void updateMonitoringEventsColumns_rejectsAllHidden() {
    AppUserRepository repository = mock(AppUserRepository.class);
    AppUser user = new AppUser();
    user.setEmail("user@example.com");
    when(repository.findByEmailIgnoreCase("user@example.com")).thenReturn(java.util.Optional.of(user));

    CurrentUserPreferencesServiceImpl service = new CurrentUserPreferencesServiceImpl(
        repository,
        mock(DashboardService.class),
        mock(TopologyService.class),
        new ObjectMapper()
    );

    var auth = new UsernamePasswordAuthenticationToken("user@example.com", "n/a");
    var columns = List.of(
        new MonitoringEventsColumnPreferenceItemDto("breachStartedAt", false),
        new MonitoringEventsColumnPreferenceItemDto("duration", false),
        new MonitoringEventsColumnPreferenceItemDto("thresholdLevel", false),
        new MonitoringEventsColumnPreferenceItemDto("status", false),
        new MonitoringEventsColumnPreferenceItemDto("deviceHostName", false),
        new MonitoringEventsColumnPreferenceItemDto("deviceName", false),
        new MonitoringEventsColumnPreferenceItemDto("metricName", false),
        new MonitoringEventsColumnPreferenceItemDto("thresholdValue", false),
        new MonitoringEventsColumnPreferenceItemDto("actualValue", false)
    );

    assertThrows(
        IllegalArgumentException.class,
        () -> service.updateMonitoringEventsColumns(new UpdateMonitoringEventsColumnsRequest(columns), auth)
    );
  }
}
