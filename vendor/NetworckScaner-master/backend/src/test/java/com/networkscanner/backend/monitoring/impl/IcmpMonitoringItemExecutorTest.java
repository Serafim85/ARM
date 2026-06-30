package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.network.scan.api.IcmpProbeResult;
import com.networkscanner.backend.network.scan.api.IcmpProbeService;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class IcmpMonitoringItemExecutorTest {

  @Test
  void executesAllIcmpItemsWithSingleProbeCall() {
    IcmpProbeService probeService = mock(IcmpProbeService.class);
    when(probeService.probe("10.10.10.1")).thenReturn(new IcmpProbeResult(3, 2, 33.3d, 0.012d, true));
    IcmpMonitoringItemExecutor executor = new IcmpMonitoringItemExecutor(probeService, new MonitoringPreprocessingEngine());

    MonitoredDeviceEntity device = new MonitoredDeviceEntity();
    device.setIp("10.10.10.1");

    List<MaterializedZabbixItem> items = List.of(
        item(runtime("icmpping", "Service state", ""), "icmpping"),
        item(runtime("icmppingloss", null, "%"), "icmppingloss"),
        item(runtime("icmppingsec", null, "s"), "icmppingsec")
    );

    List<ZabbixItemValue> values = executor.execute(
        device,
        null,
        items,
        Map.of(),
        Map.of(),
        OffsetDateTime.parse("2026-04-05T10:00:00Z")
    );

    assertEquals(3, values.size());
    Map<String, ZabbixItemValue> byKey = values.stream()
        .collect(java.util.stream.Collectors.toMap(ZabbixItemValue::itemKey, value -> value));

    assertEquals(1.0d, byKey.get("icmpping").numericValue(), 0.0001d);
    assertEquals("1", byKey.get("icmpping").textValue());
    assertEquals(33.3d, byKey.get("icmppingloss").numericValue(), 0.0001d);
    assertEquals(0.012d, byKey.get("icmppingsec").numericValue(), 0.0001d);
    verify(probeService).probe("10.10.10.1");
  }

  @Test
  void supportsOnlyZabbixIcmpSimpleItems() {
    IcmpMonitoringItemExecutor executor =
        new IcmpMonitoringItemExecutor(mock(IcmpProbeService.class), new MonitoringPreprocessingEngine());

    assertTrue(executor.supports(item(runtime("icmpping", null, ""), "icmpping")));
    assertFalse(executor.supports(item(runtime("sysName", null, ""), "sysName")));
    assertFalse(executor.supports(item(runtimeWithType("icmpping", "SNMP_AGENT"), "icmpping")));
  }

  private MaterializedZabbixItem item(ZabbixItemRuntime runtime, String key) {
    return new MaterializedZabbixItem(
        "icmp-template",
        runtime,
        key,
        key,
        "",
        null,
        null,
        Map.of()
    );
  }

  private ZabbixItemRuntime runtime(String key, String valueMapName, String units) {
    return new ZabbixItemRuntime(
        "uuid-" + key,
        key,
        key,
        "SIMPLE",
        null,
        30,
        "FLOAT",
        units,
        "",
        null,
        "",
        "",
        List.of(),
        valueMapName,
        false,
        null
    );
  }

  private ZabbixItemRuntime runtimeWithType(String key, String type) {
    return new ZabbixItemRuntime(
        "uuid-" + key,
        key,
        key,
        type,
        "1.3.6.1.2.1.1.5.0",
        30,
        "FLOAT",
        "",
        "",
        null,
        "",
        "",
        List.of(),
        null,
        false,
        null
    );
  }
}
