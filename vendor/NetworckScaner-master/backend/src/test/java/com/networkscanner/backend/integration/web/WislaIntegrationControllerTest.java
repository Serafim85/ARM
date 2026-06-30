package com.networkscanner.backend.integration.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.api.WislaBootstrapService;
import com.networkscanner.backend.integration.dto.ProbeBootstrapPageResponse;
import com.networkscanner.backend.integration.dto.ProbeBootstrapPayload;
import com.networkscanner.backend.integration.dto.WislaBootstrapRequest;
import java.time.OffsetDateTime;
import java.util.List;

import com.networkscanner.backend.monitoring.api.MonitoringService;
import com.networkscanner.backend.monitoring.dto.DeviceInterfaceDto;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;

class WislaIntegrationControllerTest {

  @Test
  void monitoredDevicesBuildsResponseEnvelopeFromServicePage() {
    WislaBootstrapService service = org.mockito.Mockito.mock(WislaBootstrapService.class);
    MonitoringService monitoringService = org.mockito.Mockito.mock(MonitoringService.class);
    ProbeBootstrapPayload payload = new ProbeBootstrapPayload(
        "networkscanner",
        1L,
        "10.0.0.1",
        "host-1",
        "Host 1",
        "SN1",
        "AA:BB:CC:DD:EE:01",
        "Cisco",
        "Model",
        "FW",
        List.of("tpl-1"),
        "tpl-1",
        "1",
        "1.0.0",
        "1.0",
        OffsetDateTime.parse("2026-04-30T09:00:00Z")
    );
    when(service.listMonitoredDevices(org.mockito.ArgumentMatchers.any()))
        .thenReturn(new PageImpl<>(List.of(payload)));

    SourceSystemProvider sourceSystemProvider = () -> "networkscanner";
    WislaIntegrationController controller = new WislaIntegrationController(service, sourceSystemProvider,monitoringService);
    OffsetDateTime updatedSince = OffsetDateTime.parse("2026-04-01T00:00:00Z");

    ProbeBootstrapPageResponse response = controller.monitoredDevices(0, 100, updatedSince);

    ArgumentCaptor<WislaBootstrapRequest> captor = ArgumentCaptor.forClass(WislaBootstrapRequest.class);
    org.mockito.Mockito.verify(service).listMonitoredDevices(captor.capture());
    assertEquals(updatedSince, captor.getValue().updatedSince());
    assertEquals("1.0", response.schemaVersion());
    assertEquals("networkscanner", response.sourceSystem());
    assertEquals(1, response.items().size());
    assertEquals(1L, response.items().get(0).externalDeviceId());
  }

  @Test
  void interfacesByIdDelegatesToMonitoringService() {
    WislaBootstrapService service = org.mockito.Mockito.mock(WislaBootstrapService.class);
    MonitoringService monitoringService = org.mockito.Mockito.mock(MonitoringService.class);
    List<DeviceInterfaceDto> expected = List.of(
        new DeviceInterfaceDto(
            "eth0",
            "eth0",
            "UP",
            "UP",
            "Нет",
            "1 Gb/s",
            "0 b/s",
            "eth0",
            "Access",
            "physical"
        )
    );
    when(monitoringService.getDeviceInterfacesById(42L)).thenReturn(expected);

    WislaIntegrationController controller =
        new WislaIntegrationController(service, () -> "networkscanner", monitoringService);

    List<DeviceInterfaceDto> response = controller.interfacesById(42L);

    verify(monitoringService).getDeviceInterfacesById(42L);
    assertEquals(expected, response);
  }
}
