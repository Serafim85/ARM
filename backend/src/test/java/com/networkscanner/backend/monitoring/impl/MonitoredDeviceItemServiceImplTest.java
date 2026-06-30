package com.networkscanner.backend.monitoring.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.monitoring.api.ZabbixRuntimeStateService;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateCoverageReportDto;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceItemEntity;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceItemEntityId;
import com.networkscanner.backend.monitoring.repository.MonitoringEventRepository;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceItemRepository;
import com.networkscanner.backend.monitoring.repository.MonitoredDeviceRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MonitoredDeviceItemServiceImplTest {

  @Test
  void deactivateItemResolvesOpenEventsAndPurgesRuntimeState() {
    MonitoredDeviceItemRepository itemRepository = mock(MonitoredDeviceItemRepository.class);
    MonitoredDeviceRepository monitoredDeviceRepository = mock(MonitoredDeviceRepository.class);
    ZabbixRuntimeStateService runtimeStateService = mock(ZabbixRuntimeStateService.class);
    MonitoringEventRepository monitoringEventRepository = mock(MonitoringEventRepository.class);

    MonitoredDeviceItemServiceImpl service = new MonitoredDeviceItemServiceImpl(
        itemRepository,
        monitoredDeviceRepository,
        runtimeStateService,
        monitoringEventRepository
    );

    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setId(101L);
    device.setIp("10.10.10.101");

    MonitoredDeviceItemEntity persisted = new MonitoredDeviceItemEntity();
    persisted.setDeviceId(101L);
    persisted.setItemUuid("item-uuid-1");
    persisted.setItemKey("cpu_current");
    persisted.setInstanceKey("");

    MonitoredDeviceItemEntityId rowId = new MonitoredDeviceItemEntityId(101L, "item-uuid-1", "");
    when(itemRepository.findById(rowId)).thenReturn(Optional.of(persisted));

    service.deactivateItem(device, templateWithSingleItem("item-uuid-1", "cpu_current"), "item-uuid-1", null);

    verify(monitoringEventRepository).resolveOpenEventsByItem(
        eq(101L),
        eq("cpu_current"),
        eq(""),
        any(OffsetDateTime.class)
    );
    verify(runtimeStateService).removeItemState(101L, "item-uuid-1", "");
    verify(itemRepository).deleteById(rowId);
    verify(monitoredDeviceRepository).save(device);
  }

  private ResolvedMonitoringTemplate templateWithSingleItem(String itemUuid, String itemKey) {
    ZabbixItemRuntime item = new ZabbixItemRuntime(
        itemUuid,
        itemKey,
        "CPU current",
        "SNMP_AGENT",
        null,
        60,
        "FLOAT",
        "%",
        null,
        null,
        null,
        null,
        null,
        null,
        false,
        null
    );
    return new ResolvedMonitoringTemplate(
        "template-id",
        "SNMP",
        "Template",
        "",
        null,
        null,
        null,
        0,
        "1",
        "1",
        "1",
        MonitoringTemplateSnmp.v2c("public", 3000, 1, 161),
        new MonitoringTemplateOids(Map.of(), Map.of(), Map.of()),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(),
        Map.of(itemKey, item),
        Map.of(),
        Map.of(),
        Map.of(),
        java.util.List.of(),
        Map.of(),
        new MonitoringTemplateCoverageReportDto(java.util.List.of(), java.util.List.of(), java.util.List.of()),
        true
    );
  }
}
