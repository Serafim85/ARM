package com.networkscanner.backend.monitoring.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.networkscanner.backend.monitoring.dto.MaterializedZabbixItem;
import com.networkscanner.backend.monitoring.dto.MonitoringPreprocessContext;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateOids;
import com.networkscanner.backend.monitoring.dto.MonitoringTemplateSnmp;
import com.networkscanner.backend.monitoring.dto.ResolvedMonitoringTemplate;
import com.networkscanner.backend.monitoring.dto.ZabbixItemRuntime;
import com.networkscanner.backend.monitoring.dto.ZabbixItemValue;
import com.networkscanner.backend.monitoring.dto.ZabbixPreprocessingStep;
import com.networkscanner.backend.monitoring.model.MonitoredDeviceEntity;
import com.networkscanner.backend.network.scan.api.SnmpScanService;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SnmpMonitoringItemExecutorTest {

  @Test
  void passesResolvedTemplateAndMaterializedItemInPreprocessContext() {
    SnmpScanService snmp = mock(SnmpScanService.class);
    MonitoringPreprocessingEngine preprocessingEngine = mock(MonitoringPreprocessingEngine.class);
    when(preprocessingEngine.process(
        any(),
        any(),
        any(),
        any(),
        any()
    )).thenReturn(new MonitoringPreprocessingEngine.ProcessedMonitoringValue(
        1.0d,
        "1",
        false,
        "ok",
        null
    ));

    ZabbixItemRuntime runtime = snmpGetRuntime("system.name", "get[1.3.6.1.2.1.1.5.0]");
    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        "tpl",
        runtime,
        "system.name",
        "system.name",
        "",
        null,
        runtime.snmpOid(),
        Map.of()
    );
    ResolvedMonitoringTemplate template = minimalTemplate(Map.of(runtime.key(), runtime));

    when(snmp.readRawOids(eq("10.0.0.5"), eq(template), any()))
        .thenReturn(Map.of("system.name", "host-x"));

    SnmpMonitoringItemExecutor executor = new SnmpMonitoringItemExecutor(snmp, preprocessingEngine);
    OffsetDateTime ts = OffsetDateTime.parse("2026-04-10T12:00:00Z");
    MonitoredDeviceEntity device = device("10.0.0.5");

    executor.execute(device, template, List.of(materialized), Map.of(), Map.of(), ts);

    ArgumentCaptor<MonitoringPreprocessContext> ctxCaptor =
        ArgumentCaptor.forClass(MonitoringPreprocessContext.class);
    verify(preprocessingEngine).process(
        eq(runtime),
        eq("host-x"),
        isNull(),
        eq(ts),
        ctxCaptor.capture()
    );
    MonitoringPreprocessContext ctx = ctxCaptor.getValue();
    assertNotNull(ctx);
    assertSame(template, ctx.template(), "preprocess context must include template (not MonitoringPreprocessContext.NONE)");
    assertSame(materialized, ctx.materializedItem());
  }

  @Test
  void walkItemWithSnmpWalkToJsonUsesContextSoPreprocessingStaysOk() {
    SnmpScanService snmp = mock(SnmpScanService.class);
    MonitoringPreprocessingEngine engine = new MonitoringPreprocessingEngine();

    ZabbixItemRuntime walk = cpuLoadWalkRuntime();
    Map<String, ZabbixItemRuntime> items = new LinkedHashMap<>();
    items.put(walk.key(), walk);
    ResolvedMonitoringTemplate template = minimalTemplate(items);

    MaterializedZabbixItem materialized = new MaterializedZabbixItem(
        "tpl",
        walk,
        walk.key(),
        walk.key(),
        "",
        null,
        walk.snmpOid(),
        Map.of()
    );

    String rawFromSnmp =
        "[{\"index\":\"1\",\"laName\":\"Load-1\",\"laLoad\":\"0.11\"},{\"index\":\"2\",\"laName\":\"Load-5\",\"laLoad\":\"0.22\"}]";
    when(snmp.readRawOids(eq("192.168.51.42"), eq(template), any()))
        .thenReturn(Map.of(walk.key(), rawFromSnmp));

    SnmpMonitoringItemExecutor executor = new SnmpMonitoringItemExecutor(snmp, engine);
    List<ZabbixItemValue> out = executor.execute(
        device("192.168.51.42"),
        template,
        List.of(materialized),
        Map.of(),
        Map.of(),
        OffsetDateTime.parse("2026-04-10T12:00:00Z")
    );

    assertEquals(1, out.size());
    ZabbixItemValue v = out.get(0);
    assertEquals("ok", v.preprocessingStatus());
    assertNull(v.preprocessingNote());
    assertTrue(v.textValue().contains("laName"));
    assertTrue(v.textValue().contains("Load-1"));
    assertTrue(v.textValue().contains("{#SNMPINDEX}"));
  }

  @Test
  void supportsSnmpAgentAndNotIcmpSimple() {
    SnmpMonitoringItemExecutor executor =
        new SnmpMonitoringItemExecutor(mock(SnmpScanService.class), new MonitoringPreprocessingEngine());

    assertTrue(executor.supports(materialized(snmpGetRuntime("system.name", "1.2.3"), "system.name")));
    assertFalse(executor.supports(materialized(simpleIcmpRuntime(), "icmpping")));
  }

  @Test
  void skipsPollingWhenOidHasUnresolvedLldMacro() {
    SnmpScanService snmp = mock(SnmpScanService.class);
    ZabbixItemRuntime badOid = snmpGetRuntime("net.if.in[ifHCInOctets.{#SNMPINDEX}]", "walk[1.2.3.{#SNMPINDEX}]");
    MaterializedZabbixItem item = materialized(badOid, badOid.key());

    SnmpMonitoringItemExecutor executor =
        new SnmpMonitoringItemExecutor(snmp, mock(MonitoringPreprocessingEngine.class));

    List<ZabbixItemValue> out = executor.execute(
        device("10.0.0.1"),
        minimalTemplate(Map.of()),
        List.of(item),
        Map.of(),
        Map.of(),
        OffsetDateTime.now()
    );

    assertTrue(out.isEmpty());
    verify(snmp, never()).readRawOids(any(), any(), any());
  }

  @Test
  void returnsEmptyValuesWhenSnmpServiceReturnsNullRawMap() {
    SnmpScanService snmp = mock(SnmpScanService.class);
    MonitoringPreprocessingEngine preprocessingEngine = mock(MonitoringPreprocessingEngine.class);
    ZabbixItemRuntime runtime = snmpGetRuntime("system.name", "get[1.3.6.1.2.1.1.5.0]");

    when(snmp.readRawOids(any(), any(), any())).thenReturn(null);

    SnmpMonitoringItemExecutor executor = new SnmpMonitoringItemExecutor(snmp, preprocessingEngine);
    List<ZabbixItemValue> out = executor.execute(
        device("10.0.0.5"),
        minimalTemplate(Map.of(runtime.key(), runtime)),
        List.of(materialized(runtime, runtime.key())),
        Map.of(),
        Map.of(),
        OffsetDateTime.parse("2026-04-10T12:00:00Z")
    );

    assertTrue(out.isEmpty());
    verify(preprocessingEngine, never()).process(any(), any(), any(), any(), any());
  }

  private static MonitoredDeviceEntity device(String ip) {
    MonitoredDeviceEntity d = new MonitoredDeviceEntity();
    d.setIp(ip);
    return d;
  }

  private static MaterializedZabbixItem materialized(ZabbixItemRuntime runtime, String key) {
    return new MaterializedZabbixItem(
        "tpl",
        runtime,
        key,
        key,
        "",
        null,
        runtime.snmpOid(),
        Map.of()
    );
  }

  private static ZabbixItemRuntime snmpGetRuntime(String key, String snmpOid) {
    return new ZabbixItemRuntime(
        "u-" + key,
        key,
        key,
        "SNMP_AGENT",
        snmpOid,
        60,
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

  private static ZabbixItemRuntime cpuLoadWalkRuntime() {
    String key = "system.cpu.load.walk";
    return new ZabbixItemRuntime(
        "u-load-walk",
        key,
        "Load walk",
        "SNMP_AGENT",
        "walk[1.3.6.1.4.1.2021.10.1.2,1.3.6.1.4.1.2021.10.1.3]",
        60,
        "TEXT",
        "",
        "",
        null,
        "",
        "",
        List.of(new ZabbixPreprocessingStep(
            "SNMP_WALK_TO_JSON",
            List.of(
                "laName", "1.3.6.1.4.1.2021.10.1.2", "0",
                "laLoad", "1.3.6.1.4.1.2021.10.1.3", "0"
            ),
            null,
            null
        )),
        null,
        false,
        null
    );
  }

  private static ZabbixItemRuntime simpleIcmpRuntime() {
    return new ZabbixItemRuntime(
        "u-ping",
        "icmpping",
        "Ping",
        "SIMPLE",
        null,
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

  private static ResolvedMonitoringTemplate minimalTemplate(Map<String, ZabbixItemRuntime> items) {
    return new ResolvedMonitoringTemplate(
        "tpl",
        "SNMP",
        "T",
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
        items,
        Map.of(),
        Map.of(),
        Map.of(),
        List.of(),
        Map.of(),
        null,
        true
    );
  }
}
