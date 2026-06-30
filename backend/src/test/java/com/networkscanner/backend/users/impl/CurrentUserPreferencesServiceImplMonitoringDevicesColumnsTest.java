package com.networkscanner.backend.users.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.dashboards.api.DashboardService;
import com.networkscanner.backend.topology.api.TopologyService;
import com.networkscanner.backend.users.dto.MonitoringDevicesColumnPreferenceItemDto;
import com.networkscanner.backend.users.dto.UpdateMonitoringDevicesColumnsRequest;
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

class CurrentUserPreferencesServiceImplMonitoringDevicesColumnsTest {

  @Test
  void updateMonitoringDevicesColumns_persistsValidPreference() throws Exception {
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
        new MonitoringDevicesColumnPreferenceItemDto("hostName", true),
        new MonitoringDevicesColumnPreferenceItemDto("name", true),
        new MonitoringDevicesColumnPreferenceItemDto("deviceParams", true),
        new MonitoringDevicesColumnPreferenceItemDto("series", false),
        new MonitoringDevicesColumnPreferenceItemDto("model", true),
        new MonitoringDevicesColumnPreferenceItemDto("firmwareVersion", true),
        new MonitoringDevicesColumnPreferenceItemDto("availability", true),
        new MonitoringDevicesColumnPreferenceItemDto("protocol", true),
        new MonitoringDevicesColumnPreferenceItemDto("healthStatus", true),
        new MonitoringDevicesColumnPreferenceItemDto("tags", true),
        new MonitoringDevicesColumnPreferenceItemDto("actions", true)
    );

    var saved = service.updateMonitoringDevicesColumns(new UpdateMonitoringDevicesColumnsRequest(columns), auth);

    assertEquals(columns, saved.columns());
    verify(repository).save(user);
    assertEquals(new ObjectMapper().writeValueAsString(columns), user.getMonitoringDevicesColumnsJson());
  }

  @Test
  void getMonitoringDevicesColumns_returnsNullWhenUnset() {
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
    assertNull(service.getMonitoringDevicesColumns(auth).columns());
  }

  @Test
  void updateMonitoringDevicesColumns_rejectsAllHidden() {
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
        new MonitoringDevicesColumnPreferenceItemDto("hostName", false),
        new MonitoringDevicesColumnPreferenceItemDto("name", false),
        new MonitoringDevicesColumnPreferenceItemDto("deviceParams", false),
        new MonitoringDevicesColumnPreferenceItemDto("series", false),
        new MonitoringDevicesColumnPreferenceItemDto("model", false),
        new MonitoringDevicesColumnPreferenceItemDto("firmwareVersion", false),
        new MonitoringDevicesColumnPreferenceItemDto("availability", false),
        new MonitoringDevicesColumnPreferenceItemDto("protocol", false),
        new MonitoringDevicesColumnPreferenceItemDto("healthStatus", false),
        new MonitoringDevicesColumnPreferenceItemDto("tags", false),
        new MonitoringDevicesColumnPreferenceItemDto("actions", false)
    );

    assertThrows(
        IllegalArgumentException.class,
        () -> service.updateMonitoringDevicesColumns(new UpdateMonitoringDevicesColumnsRequest(columns), auth)
    );
  }
}
