package com.networkscanner.backend.users.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.networkscanner.backend.dashboards.api.DashboardService;
import com.networkscanner.backend.topology.api.TopologyService;
import com.networkscanner.backend.users.dto.UpdateTableColumnWidthsRequest;
import com.networkscanner.backend.users.model.AppUser;
import com.networkscanner.backend.users.repository.AppUserRepository;
import com.networkscanner.backend.users.util.TableColumnWidthTableKeys;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class CurrentUserPreferencesServiceImplTableColumnWidthsTest {

  @Test
  void updateTableColumnWidths_mergesAndClamps() {
    AppUserRepository repo = Mockito.mock(AppUserRepository.class);
    DashboardService dashboards = Mockito.mock(DashboardService.class);
    TopologyService topology = Mockito.mock(TopologyService.class);
    ObjectMapper objectMapper = new ObjectMapper();

    CurrentUserPreferencesServiceImpl service = new CurrentUserPreferencesServiceImpl(
        repo,
        dashboards,
        topology,
        objectMapper
    );

    AppUser user = new AppUser();
    user.setEmail("admin@example.com");
    when(repo.findByEmailIgnoreCase("admin@example.com")).thenReturn(java.util.Optional.of(user));
    when(repo.save(any(AppUser.class))).thenAnswer(invocation -> invocation.getArgument(0));

    var auth = new UsernamePasswordAuthenticationToken("admin@example.com", "n/a");

    var saved = service.updateTableColumnWidths(
        new UpdateTableColumnWidthsRequest(
            TableColumnWidthTableKeys.DEVICES,
            Map.of("hostName", 200, "name", 900)
        ),
        auth
    );

    assertThat(saved.widths()).containsKey(TableColumnWidthTableKeys.DEVICES);
    assertThat(saved.widths().get(TableColumnWidthTableKeys.DEVICES)).containsEntry("hostName", 200);
    assertThat(saved.widths().get(TableColumnWidthTableKeys.DEVICES)).containsEntry("name", 640);

    var cleared = service.updateTableColumnWidths(
        new UpdateTableColumnWidthsRequest(TableColumnWidthTableKeys.DEVICES, Map.of()),
        auth
    );
    assertThat(cleared.widths()).doesNotContainKey(TableColumnWidthTableKeys.DEVICES);
  }
}
