package com.networkscanner.backend.integration.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.integration.api.SourceSystemProvider;
import com.networkscanner.backend.integration.dto.ProbeBootstrapPayload;
import com.networkscanner.backend.integration.dto.WislaBootstrapRequest;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

class WislaBootstrapServiceImplTest {

  @Test
  void listMonitoredDevicesReturnsMappedPage() {
    MonitoredDeviceRepository repository = org.mockito.Mockito.mock(MonitoredDeviceRepository.class);
    ProbeBootstrapPayloadMapper mapper = org.mockito.Mockito.mock(ProbeBootstrapPayloadMapper.class);
    MonitoredDeviceEntity entity = new MonitoredDeviceEntity();
    entity.setId(101L);
    when(repository.findAll(any(Specification.class), any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(entity)));
    when(mapper.map(entity, "networkscanner"))
        .thenReturn(new ProbeBootstrapPayload(
            "networkscanner",
            101L,
            "10.0.0.101",
            "sw-101",
            "Switch 101",
            "SN101",
            "AA:BB:CC:DD:EE:11",
            "Cisco",
            "C9200",
            "17.9",
            List.of("tpl"),
            "tpl",
            "1",
            "1.0.0",
            "1.0",
            OffsetDateTime.parse("2026-04-30T10:00:00Z")
        ));

    SourceSystemProvider sourceSystemProvider = () -> "networkscanner";
    WislaBootstrapServiceImpl service = new WislaBootstrapServiceImpl(repository, mapper, sourceSystemProvider);

    Page<ProbeBootstrapPayload> result = service.listMonitoredDevices(new WislaBootstrapRequest(0, 100, null));

    assertEquals(1, result.getTotalElements());
    assertEquals(101L, result.getContent().get(0).externalDeviceId());
    verify(mapper).map(entity, "networkscanner");
  }

  @Test
  void listMonitoredDevicesUsesIdAscPaging() {
    MonitoredDeviceRepository repository = org.mockito.Mockito.mock(MonitoredDeviceRepository.class);
    ProbeBootstrapPayloadMapper mapper = org.mockito.Mockito.mock(ProbeBootstrapPayloadMapper.class);
    when(repository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(Page.empty());

    SourceSystemProvider sourceSystemProvider = () -> "networkscanner";
    WislaBootstrapServiceImpl service = new WislaBootstrapServiceImpl(repository, mapper, sourceSystemProvider);
    service.listMonitoredDevices(new WislaBootstrapRequest(2, 50, null));

    ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
    verify(repository).findAll(any(Specification.class), pageableCaptor.capture());
    Pageable pageable = pageableCaptor.getValue();
    assertEquals(2, pageable.getPageNumber());
    assertEquals(50, pageable.getPageSize());
    assertEquals("id: ASC", pageable.getSort().toString());
  }
}
